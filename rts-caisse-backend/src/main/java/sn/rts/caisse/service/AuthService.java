package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.audit.AuditAction;
import sn.rts.caisse.audit.AuditService;
import sn.rts.caisse.dto.auth.AuthResponse;
import sn.rts.caisse.dto.auth.LoginRequest;
import sn.rts.caisse.model.Utilisateur;
import sn.rts.caisse.repository.UtilisateurRepository;
import sn.rts.caisse.security.AccountLockedException;
import sn.rts.caisse.security.JwtService;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Authentifie un utilisateur et émet un JWT signé.
 *
 * <h2>Verrouillage de compte</h2>
 * <p>En complement du rate limiting par IP, on bloque le compte lui-meme
 * apres {@value #MAX_FAILED_ATTEMPTS} echecs successifs. Le verrou dure
 * {@value #LOCK_MINUTES} minutes et se reset automatiquement, ou est
 * remis a zero par un login reussi.</p>
 *
 * <p>Cette protection est complementaire du {@code LoginRateLimitService}
 * (rate limit par IP) : ici on protege le compte cible, peu importe
 * l'origine des tentatives.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Nombre max d'echecs consecutifs avant verrouillage du compte. */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** Duree du lock automatique apres depassement du seuil. */
    public static final long LOCK_MINUTES = 30;

    private final AuthenticationManager authenticationManager;
    private final UtilisateurRepository utilisateurRepository;
    private final JwtService jwtService;
    private final AuditService auditService;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // -------- 1. Pre-check : compte deja verrouille ? --------
        // On regarde si l'utilisateur existe et s'il est sous lock temporaire.
        // Si oui, on rejette immediatement avec une AccountLockedException et
        // le nombre de secondes restantes avant deverrouillage automatique.
        // Note : si le login n'existe pas, on laisse passer pour ne pas
        // reveler l'existence ou non d'un compte (timing attack mitigation).
        Utilisateur preCheck = utilisateurRepository.findByLogin(request.login()).orElse(null);
        if (preCheck != null && preCheck.isLocked()) {
            long secondsRemaining = Math.max(1,
                    Duration.between(LocalDateTime.now(), preCheck.getLockedUntil()).getSeconds());
            auditService.logLoginFailed(request.login(),
                    "Compte verrouille (encore " + secondsRemaining + "s)");
            throw new AccountLockedException(secondsRemaining);
        }

        // -------- 2. Tentative d'authentification --------
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.login(), request.motDePasse()));

            Utilisateur utilisateur = utilisateurRepository.findByLogin(request.login())
                    .orElseThrow(() -> new UsernameNotFoundException(
                            "Utilisateur introuvable après authentification."));

            // -------- 3a. Succes : reset du compteur + emission JWT --------
            if (utilisateur.getFailedLoginAttempts() != 0
                    || utilisateur.getLockedUntil() != null) {
                utilisateur.setFailedLoginAttempts(0);
                utilisateur.setLockedUntil(null);
                utilisateurRepository.save(utilisateur);
            }

            String token = jwtService.generateToken(utilisateur);

            auditService.logForUser(
                    AuditAction.LOGIN_SUCCESS,
                    utilisateur,
                    "Utilisateur", utilisateur.getId(), utilisateur.getLogin(),
                    true, null,
                    "Rôle: " + utilisateur.getRole());

            log.info("Connexion réussie : {} ({})",
                    utilisateur.getLogin(), utilisateur.getRole());

            return AuthResponse.bearer(token, jwtService.getExpirationMs(),
                    utilisateur.getId(), utilisateur.getMatricule(),
                    utilisateur.getLogin(), utilisateur.getNomComplet(),
                    utilisateur.getRole());

        } catch (LockedException e) {
            // Spring Security a rejete car isAccountNonLocked() = false.
            // Couvre le cas ou le user etait verrouille apres notre pre-check
            // (race) ou desactive par admin. On audit + on remonte.
            auditService.logLoginFailed(request.login(),
                    "Compte verrouille (LockedException)");
            // On remonte une AccountLockedException pour avoir un 423 propre
            // cote frontend. Si pas de lockedUntil, on suppose 0s.
            long retry = 0;
            if (preCheck != null && preCheck.getLockedUntil() != null) {
                retry = Math.max(1, Duration.between(
                        LocalDateTime.now(), preCheck.getLockedUntil()).getSeconds());
            }
            throw new AccountLockedException(retry);

        } catch (org.springframework.security.core.AuthenticationException e) {
            // -------- 3b. Echec : incrementer le compteur d'echecs --------
            // On ne recharge l'utilisateur que s'il existe (le login peut etre
            // tape n'importe comment). On n'incremente jamais le compteur pour
            // un login inexistant : ne pas creer du bruit dans la base et ne
            // pas trahir si un compte existe ou non.
            if (preCheck != null) {
                Utilisateur fresh = utilisateurRepository.findByLogin(request.login())
                        .orElse(null);
                if (fresh != null) {
                    int nouvelle = fresh.getFailedLoginAttempts() + 1;
                    fresh.setFailedLoginAttempts(nouvelle);
                    if (nouvelle >= MAX_FAILED_ATTEMPTS) {
                        fresh.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                        log.warn("[ACCOUNT-LOCK] Compte {} verrouille pour {} min ({} echecs)",
                                fresh.getLogin(), LOCK_MINUTES, nouvelle);
                        auditService.logForUser(
                                AuditAction.COMPTE_VERROUILLE,
                                fresh,
                                "Utilisateur", fresh.getId(), fresh.getLogin(),
                                false,
                                "Trop d'echecs successifs",
                                "Tentatives=" + nouvelle
                                        + " LockedUntil=" + fresh.getLockedUntil());
                    }
                    utilisateurRepository.save(fresh);
                }
            }
            auditService.logLoginFailed(request.login(), e.getMessage());
            throw e;
        }
    }
}
