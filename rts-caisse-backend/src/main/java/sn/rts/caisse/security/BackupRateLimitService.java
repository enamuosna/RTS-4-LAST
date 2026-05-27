package sn.rts.caisse.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limiter pour les operations sensibles de sauvegarde / restauration
 * de la base de donnees ({@code /api/backup/export}, {@code /api/backup/import}).
 *
 * <h2>Politique</h2>
 * <ul>
 *   <li><b>Une operation backup (export OU import) max par utilisateur
 *       toutes les {@value #WINDOW_MINUTES} minutes.</b></li>
 *   <li>Limitation par login (pas par IP) : si un compte ADMIN est compromis,
 *       l'attaquant ne peut pas spammer l'export pour exfiltrer la BDD a
 *       grande vitesse.</li>
 *   <li>Compte par login : l'export et l'import partagent le meme compteur.</li>
 * </ul>
 *
 * <h2>Justification</h2>
 * <p>Un export ou un import de BDD est une operation <b>ponctuelle</b>, jamais
 * recurrente. Si un meme admin fait 5 exports en 10 min, c'est anormal
 * (probablement un attaquant qui exfiltre + iterates). On bloque.</p>
 *
 * <p>En memoire (ConcurrentHashMap), eviction lazy : suffisant pour un
 * deploiement single-instance (1 conteneur Docker).</p>
 */
@Slf4j
@Service
public class BackupRateLimitService {

    /** Delai minimum entre 2 operations backup pour un meme utilisateur. */
    public static final long WINDOW_MINUTES = 60;

    private static final Duration WINDOW = Duration.ofMinutes(WINDOW_MINUTES);

    /** Map login -> instant de la derniere operation backup. */
    private final ConcurrentMap<String, Instant> lastBackupByLogin = new ConcurrentHashMap<>();

    /**
     * Verifie si l'utilisateur peut effectuer une operation backup
     * maintenant. Retourne le nombre de secondes a attendre si bloque.
     */
    public Optional<Long> checkAllowed(String login) {
        if (login == null || login.isBlank()) return Optional.empty();
        Instant lastOp = lastBackupByLogin.get(login);
        if (lastOp == null) return Optional.empty();
        Instant now = Instant.now();
        Instant nextAllowed = lastOp.plus(WINDOW);
        if (now.isAfter(nextAllowed)) {
            // Fenetre expiree, on nettoie au passage
            lastBackupByLogin.remove(login, lastOp);
            return Optional.empty();
        }
        long secondsRemaining = Math.max(1, Duration.between(now, nextAllowed).getSeconds());
        log.warn("[BACKUP-RATE-LIMIT] {} bloque : derniere operation a {}, "
                        + "il reste {} secondes avant deblocage",
                login, lastOp, secondsRemaining);
        return Optional.of(secondsRemaining);
    }

    /**
     * Enregistre qu'une operation backup vient d'avoir lieu. A appeler
     * APRES verification {@link #checkAllowed(String)} et succes de
     * l'operation backup.
     */
    public void recordOperation(String login) {
        if (login == null || login.isBlank()) return;
        lastBackupByLogin.put(login, Instant.now());
    }
}
