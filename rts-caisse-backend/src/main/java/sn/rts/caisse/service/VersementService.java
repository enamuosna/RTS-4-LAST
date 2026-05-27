package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sn.rts.caisse.audit.AuditAction;
import sn.rts.caisse.audit.AuditService;
import sn.rts.caisse.dto.VersementResponse;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.Banque;
import sn.rts.caisse.model.Caisse;
import sn.rts.caisse.model.JournalCaisse;
import sn.rts.caisse.model.Role;
import sn.rts.caisse.model.Utilisateur;
import sn.rts.caisse.model.Versement;
import sn.rts.caisse.repository.BanqueRepository;
import sn.rts.caisse.repository.CaisseRepository;
import sn.rts.caisse.repository.JournalCaisseRepository;
import sn.rts.caisse.repository.UtilisateurRepository;
import sn.rts.caisse.repository.VersementRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Gestion des versements bancaires depuis les caisses RTS.
 *
 * <h2>Règles métier</h2>
 * <ul>
 *   <li>Tout versement est rattaché à une <b>caisse</b> et une <b>banque</b>.
 *       Il peut éventuellement être lié à un <b>journal</b> (utile pour
 *       l'export Excel par période).</li>
 *   <li>Le bordereau bancaire (PDF, JPG ou PNG, max 5 Mo) est <b>obligatoire</b>.</li>
 *   <li>CAISSIER et AGENT_RECETTE ne peuvent créer/supprimer que pour leur
 *       caisse affectée. ADMIN et SUPERVISEUR ont accès global.</li>
 *   <li>Suppression : autorisée pour ADMIN à tout moment, et pour l'auteur
 *       tant que le journal lié (s'il existe) n'est pas clôturé.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class VersementService {

    /** Taille max du fichier bordereau : 5 Mo. */
    public static final long TAILLE_MAX_FICHIER = 5L * 1024 * 1024;

    /** Types MIME autorisés pour le bordereau. */
    public static final Set<String> TYPES_MIME_AUTORISES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/jpg",
            "image/png"
    );

    private final VersementRepository     versementRepository;
    private final CaisseRepository        caisseRepository;
    private final BanqueRepository        banqueRepository;
    private final JournalCaisseRepository journalRepository;
    private final UtilisateurRepository   utilisateurRepository;
    private final AuditService            auditService;

    // ==================================================================
    //  CREATION
    // ==================================================================

    public VersementResponse enregistrer(Long caisseId,
                                          Long banqueId,
                                          Long journalId,
                                          BigDecimal montant,
                                          String numeroBordereau,
                                          LocalDateTime dateVersement,
                                          String notes,
                                          MultipartFile fichier,
                                          String loginAuteur) {
        try {
            validerFichier(fichier);

            Caisse caisse = caisseRepository.findById(caisseId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Caisse", caisseId));
            Banque banque = banqueRepository.findById(banqueId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Banque", banqueId));
            if (!Boolean.TRUE.equals(banque.getActif())) {
                throw new BusinessException("Banque inactive : " + banque.getCode());
            }
            JournalCaisse journal = null;
            if (journalId != null) {
                journal = journalRepository.findById(journalId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Journal", journalId));
                if (!journal.getCaisse().getId().equals(caisseId)) {
                    throw new BusinessException(
                            "Le journal selectionne n'appartient pas a cette caisse.");
                }
            }

            Utilisateur auteur = utilisateurRepository.findByLogin(loginAuteur)
                    .orElseThrow(() -> new BusinessException(
                            "Utilisateur introuvable : " + loginAuteur));
            verifierDroitsCaisse(auteur, caisse);

            byte[] contenu;
            try {
                contenu = fichier.getBytes();
            } catch (IOException e) {
                throw new BusinessException(
                        "Impossible de lire le fichier uploade : " + e.getMessage());
            }

            Versement v = Versement.builder()
                    .caisse(caisse)
                    .banque(banque)
                    .journal(journal)
                    .montant(montant)
                    .numeroBordereau(numeroBordereau)
                    .dateVersement(dateVersement != null ? dateVersement : LocalDateTime.now())
                    .notes(notes)
                    .fichierBordereau(contenu)
                    .nomFichier(fichier.getOriginalFilename())
                    .typeMime(fichier.getContentType())
                    .tailleFichier(fichier.getSize())
                    .createdBy(auteur)
                    .build();

            Versement saved = versementRepository.save(v);
            log.info("Versement enregistre : caisse={} banque={} montant={} fichier={} ({} octets) par {}",
                    caisse.getCode(), banque.getCode(), montant,
                    fichier.getOriginalFilename(), fichier.getSize(), loginAuteur);

            auditService.logSuccess(
                    AuditAction.CREER_VERSEMENT,
                    "Versement",
                    saved.getId(),
                    "bord=" + numeroBordereau,
                    "Caisse=" + caisse.getCode()
                            + " Banque=" + banque.getCode()
                            + " Montant=" + montant + " FCFA"
                            + " Bordereau=" + numeroBordereau
                            + " Fichier=" + fichier.getOriginalFilename()
                            + " (" + fichier.getSize() + " octets)"
                            + " Par=" + loginAuteur);

            return VersementResponse.from(saved);

        } catch (BusinessException | ResourceNotFoundException e) {
            auditService.logFailure(
                    AuditAction.CREER_VERSEMENT,
                    "Versement",
                    null,
                    "caisseId=" + caisseId + " banqueId=" + banqueId + " montant=" + montant,
                    e.getMessage());
            throw e;
        }
    }

    private void validerFichier(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new BusinessException(
                    "Le bordereau bancaire est obligatoire (PDF, JPG ou PNG).");
        }
        if (fichier.getSize() > TAILLE_MAX_FICHIER) {
            throw new BusinessException(
                    "Fichier trop volumineux : " + (fichier.getSize() / 1024)
                            + " Ko. Taille max autorisee : "
                            + (TAILLE_MAX_FICHIER / 1024 / 1024) + " Mo.");
        }
        String type = fichier.getContentType();
        if (type == null || !TYPES_MIME_AUTORISES.contains(type.toLowerCase())) {
            throw new BusinessException(
                    "Format de fichier non supporte : " + type
                            + ". Formats acceptes : PDF, JPG, PNG.");
        }
    }

    private void verifierDroitsCaisse(Utilisateur user, Caisse caisse) {
        Role role = user.getRole();
        if (role == Role.ADMIN || role == Role.SUPERVISEUR) {
            return; // OK, acces global
        }
        if (role == Role.AGENT_RECETTE) {
            Utilisateur agent = caisse.getAgentRecette();
            if (agent != null && user.getId().equals(agent.getId())) return;
            throw new BusinessException(
                    "Vous n'etes pas l'agent de recette affecte a cette caisse.");
        }
        if (role == Role.CAISSIER) {
            Utilisateur caissier = caisse.getCaissier();
            if (caissier != null && user.getId().equals(caissier.getId())) return;
            throw new BusinessException(
                    "Vous n'etes pas le caissier affecte a cette caisse.");
        }
        throw new BusinessException(
                "Action reservee au personnel autorise sur cette caisse.");
    }

    // ==================================================================
    //  LECTURES
    // ==================================================================

    @Transactional(readOnly = true)
    public Page<VersementResponse> listerParCaisse(Long caisseId, Pageable pageable) {
        return versementRepository
                .findByCaisseIdOrderByDateVersementDesc(caisseId, pageable)
                .map(VersementResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<VersementResponse> listerParCaisseEtPeriode(Long caisseId,
                                                             LocalDate dateDebut,
                                                             LocalDate dateFin,
                                                             Pageable pageable) {
        if (dateDebut == null && dateFin == null) {
            return listerParCaisse(caisseId, pageable);
        }
        LocalDate d1 = dateDebut != null ? dateDebut : LocalDate.now().minusYears(10);
        LocalDate d2 = dateFin   != null ? dateFin   : LocalDate.now();
        if (d2.isBefore(d1)) { LocalDate tmp = d1; d1 = d2; d2 = tmp; }
        return versementRepository
                .findByCaisseIdAndDateVersementBetweenOrderByDateVersementDesc(
                        caisseId, d1.atStartOfDay(), d2.plusDays(1).atStartOfDay(), pageable)
                .map(VersementResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<VersementResponse> listerTous(Pageable pageable) {
        return versementRepository
                .findAllByOrderByDateVersementDesc(pageable)
                .map(VersementResponse::from);
    }

    @Transactional(readOnly = true)
    public VersementResponse obtenir(Long id) {
        return VersementResponse.from(trouver(id));
    }

    private Versement trouver(Long id) {
        return versementRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Versement", id));
    }

    /** Charge le fichier bordereau (byte[] LAZY) pour téléchargement. */
    @Transactional(readOnly = true)
    public Versement telechargerFichier(Long id, String loginAuteur) {
        Versement v = trouver(id);
        // Force le chargement du LOB avant retour
        v.getFichierBordereau();
        auditService.logSuccess(
                AuditAction.TELECHARGER_BORDEREAU,
                "Versement",
                id,
                "bord=" + v.getNumeroBordereau(),
                "Telechargement par " + loginAuteur);
        return v;
    }

    // ==================================================================
    //  SUPPRESSION
    // ==================================================================

    public void supprimer(Long id, String loginAuteur) {
        Versement v = trouver(id);
        Utilisateur user = utilisateurRepository.findByLogin(loginAuteur)
                .orElseThrow(() -> new BusinessException(
                        "Utilisateur introuvable : " + loginAuteur));
        Role role = user.getRole();

        boolean estAdmin = role == Role.ADMIN;
        boolean estAuteur = v.getCreatedBy().getId().equals(user.getId());
        boolean journalCloture = v.getJournal() != null && v.getJournal().isCloture();

        if (!estAdmin) {
            if (!estAuteur) {
                throw new BusinessException(
                        "Seul l'auteur du versement ou un ADMIN peut le supprimer.");
            }
            if (journalCloture) {
                throw new BusinessException(
                        "Impossible de supprimer : le journal lie est cloture.");
            }
        }

        versementRepository.delete(v);
        log.info("Versement {} supprime par {} (montant={})",
                id, loginAuteur, v.getMontant());

        auditService.logSuccess(
                AuditAction.SUPPRIMER_VERSEMENT,
                "Versement",
                id,
                "bord=" + v.getNumeroBordereau(),
                "Caisse=" + v.getCaisse().getCode()
                        + " Montant=" + v.getMontant() + " FCFA"
                        + " ParAdmin=" + estAdmin
                        + " Par=" + loginAuteur);
    }
}
