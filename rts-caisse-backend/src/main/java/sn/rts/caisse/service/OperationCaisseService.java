package sn.rts.caisse.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.audit.AuditAction;
import sn.rts.caisse.audit.AuditService;
import sn.rts.caisse.dto.EnvoiWhatsAppResponse;
import sn.rts.caisse.dto.OperationCaisseRequest;
import sn.rts.caisse.dto.OperationCaisseResponse;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.Banque;
import sn.rts.caisse.model.Caisse;
import sn.rts.caisse.model.CategorieOperation;
import sn.rts.caisse.model.Client;
import sn.rts.caisse.model.ModePaiement;
import sn.rts.caisse.model.OperationCaisse;
import sn.rts.caisse.model.StatutCaisse;
import sn.rts.caisse.model.TypeOperation;
import sn.rts.caisse.model.Utilisateur;
import sn.rts.caisse.repository.BanqueRepository;
import sn.rts.caisse.repository.OperationCaisseRepository;
import sn.rts.caisse.repository.UtilisateurRepository;
import sn.rts.caisse.util.NumeroRecuGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cœur métier : enregistrement d'une opération de caisse avec toutes les règles :
 * <ul>
 *   <li>la caisse doit être <b>OUVERTE</b></li>
 *   <li>la catégorie doit correspondre au type d'opération demandé</li>
 *   <li>le solde courant doit rester positif pour les sorties</li>
 *   <li>une <b>banque</b> est obligatoire pour les règlements par chèque
 *       ou virement (v2)</li>
 *   <li>le solde courant est mis à jour transactionnellement</li>
 *   <li>un numéro de reçu unique est généré</li>
 *   <li>une opération n'est jamais supprimée : elle est <b>annulée</b>
 *       avec contre-passation du solde</li>
 * </ul>
 *
 * <p><b>Audit :</b> chaque création et annulation est tracée dans
 * {@code audit_logs} via {@link AuditService}, en succès comme en échec.
 * Les écritures d'audit utilisent une transaction {@code REQUIRES_NEW}
 * et survivent donc à un rollback métier.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OperationCaisseService {

    private final OperationCaisseRepository operationRepository;
    private final UtilisateurRepository     utilisateurRepository;
    private final BanqueRepository          banqueRepository;
    private final CaisseService             caisseService;
    private final CategorieOperationService categorieService;
    private final ClientService             clientService;
    private final NumeroRecuGenerator       numeroRecuGenerator;
    private final AuditService              auditService;

    // ==================================================================
    //  ENREGISTREMENT
    // ==================================================================

    public OperationCaisseResponse enregistrer(OperationCaisseRequest request,
                                               String loginCaissier) {
        try {
            // ---------- 1. Caisse ----------
            Caisse caisse = caisseService.trouver(request.caisseId());
            if (caisse.getStatut() != StatutCaisse.OUVERTE) {
                throw new BusinessException(
                        "La caisse " + caisse.getCode()
                                + " doit être ouverte pour saisir une opération.");
            }

            // ---------- 2. Catégorie ----------
            CategorieOperation categorie = categorieService.trouver(request.categorieId());
            if (categorie.getTypeOperation() != request.typeOperation()) {
                throw new BusinessException(
                        "La catégorie '" + categorie.getLibelle()
                                + "' ne correspond pas au type d'opération demandé ("
                                + request.typeOperation() + ").");
            }
            if (!categorie.isActif()) {
                throw new BusinessException(
                        "Catégorie désactivée : " + categorie.getLibelle());
            }

            // ---------- 3. Solde suffisant pour les sorties ----------
            if (request.typeOperation() == TypeOperation.SORTIE
                    && caisse.getSoldeCourant().compareTo(request.montant()) < 0) {
                throw new BusinessException(
                        "Solde insuffisant : solde courant = " + caisse.getSoldeCourant()
                                + " FCFA, montant demandé = " + request.montant() + " FCFA.");
            }

            // ---------- 4. Banque (CHEQUE / VIREMENT) ----------
            ModePaiement mode = request.modePaiement();
            boolean banqueObligatoire = (mode == ModePaiement.CHEQUE
                    || mode == ModePaiement.VIREMENT);
            if (banqueObligatoire && request.banqueId() == null) {
                throw new BusinessException(
                        "Une banque doit être renseignée pour les règlements par "
                                + mode.getLibelle().toLowerCase() + ".");
            }
            Banque banque = null;
            if (request.banqueId() != null) {
                banque = banqueRepository.findById(request.banqueId())
                        .orElseThrow(() -> ResourceNotFoundException.of(
                                "Banque", request.banqueId()));
                if (!Boolean.TRUE.equals(banque.getActif())) {
                    throw new BusinessException(
                            "Banque inactive : " + banque.getCode()
                                    + " - " + banque.getLibelle());
                }
            }

            // ---------- 5. Caissier ----------
            Utilisateur caissier = utilisateurRepository.findByLogin(loginCaissier)
                    .orElseThrow(() -> new BusinessException(
                            "Caissier introuvable : " + loginCaissier));

            // ---------- 6. Client (optionnel) ----------
            Client client = request.clientId() != null
                    ? clientService.trouver(request.clientId())
                    : null;

            // ---------- 7. Construction de l'opération ----------
            OperationCaisse operation = OperationCaisse.builder()
                    .numeroRecu(numeroRecuGenerator.generer(caisse.getCode()))
                    .typeOperation(request.typeOperation())
                    .montant(request.montant())
                    .motif(request.motif())
                    .modePaiement(mode)
                    .reference(request.reference())
                    .dateOperation(LocalDateTime.now())
                    .caisse(caisse)
                    .caissier(caissier)
                    .categorie(categorie)
                    .client(client)
                    .banque(banque)
                    .annulee(false)
                    .build();

            // ---------- 8. Mise à jour du solde ----------
            caisse.setSoldeCourant(applyMontant(
                    caisse.getSoldeCourant(),
                    request.typeOperation(),
                    request.montant()));

            OperationCaisse saved = operationRepository.save(operation);
            log.info("Opération enregistrée : {} ({} FCFA) - caisse {}",
                    saved.getNumeroRecu(), saved.getMontant(), caisse.getCode());

            // ---------- 9. Audit succès ----------
            auditService.logSuccess(
                    AuditAction.CREER_OPERATION,
                    "OperationCaisse",
                    saved.getId(),
                    saved.getNumeroRecu(),
                    "Type=" + saved.getTypeOperation()
                            + " Montant=" + saved.getMontant() + " FCFA"
                            + " Mode=" + saved.getModePaiement()
                            + " Caisse=" + caisse.getCode()
                            + " Catégorie=" + categorie.getLibelle()
                            + (client != null
                            ? " Client=" + client.getRaisonSociale() : "")
                            + (banque != null
                            ? " Banque=" + banque.getCode() : "")
                            + " NouveauSolde=" + caisse.getSoldeCourant() + " FCFA");

            return OperationCaisseResponse.from(saved);

        } catch (BusinessException | ResourceNotFoundException e) {
            // Audit échec (transaction REQUIRES_NEW dans AuditService)
            auditService.logFailure(
                    AuditAction.CREER_OPERATION,
                    "OperationCaisse",
                    null,
                    request != null
                            ? "type=" + request.typeOperation()
                            + " montant=" + request.montant()
                            + " mode=" + request.modePaiement()
                            + " caisseId=" + request.caisseId()
                            + " banqueId=" + request.banqueId()
                            : null,
                    e.getMessage());
            throw e;
        }
    }

    // ==================================================================
    //  ANNULATION (contre-passation)
    // ==================================================================

    public OperationCaisseResponse annuler(Long operationId,
                                           String motif,
                                           String loginAnnulateur) {
        OperationCaisse operation;
        try {
            operation = trouver(operationId);

            if (operation.isAnnulee()) {
                throw new BusinessException("Opération déjà annulée.");
            }
            if (operation.getJournal() != null && operation.getJournal().isCloture()) {
                throw new BusinessException(
                        "Impossible d'annuler : la journée de caisse est déjà clôturée.");
            }

            Caisse caisse = operation.getCaisse();
            BigDecimal soldeAvant = caisse.getSoldeCourant();

            // Contre-passation : on inverse l'effet initial sur le solde
            TypeOperation inverse = (operation.getTypeOperation() == TypeOperation.ENTREE)
                    ? TypeOperation.SORTIE
                    : TypeOperation.ENTREE;

            if (inverse == TypeOperation.SORTIE
                    && caisse.getSoldeCourant().compareTo(operation.getMontant()) < 0) {
                throw new BusinessException(
                        "Impossible d'annuler : solde courant insuffisant pour contre-passer.");
            }

            caisse.setSoldeCourant(applyMontant(
                    caisse.getSoldeCourant(),
                    inverse,
                    operation.getMontant()));

            operation.setAnnulee(true);
            operation.setMotifAnnulation(motif + " (par " + loginAnnulateur + ")");

            log.info("Annulation de l'opération {} par {} : {}",
                    operation.getNumeroRecu(), loginAnnulateur, motif);

            auditService.logSuccess(
                    AuditAction.ANNULER_OPERATION,
                    "OperationCaisse",
                    operation.getId(),
                    operation.getNumeroRecu(),
                    "Type=" + operation.getTypeOperation()
                            + " Montant=" + operation.getMontant() + " FCFA"
                            + " Mode=" + operation.getModePaiement()
                            + " Caisse=" + caisse.getCode()
                            + " SoldeAvant=" + soldeAvant + " FCFA"
                            + " SoldeApres=" + caisse.getSoldeCourant() + " FCFA"
                            + " Motif=" + motif
                            + " AnnuléePar=" + loginAnnulateur);

            return OperationCaisseResponse.from(operation);

        } catch (BusinessException | ResourceNotFoundException e) {
            auditService.logFailure(
                    AuditAction.ANNULER_OPERATION,
                    "OperationCaisse",
                    operationId,
                    "motif=" + motif + " par=" + loginAnnulateur,
                    e.getMessage());
            throw e;
        }
    }

    // ==================================================================
    //  LECTURES
    // ==================================================================

    @Transactional(readOnly = true)
    public Page<OperationCaisseResponse> historiqueParCaisse(Long caisseId, Pageable pageable) {
        return operationRepository.findByCaisseId(caisseId, pageable)
                .map(OperationCaisseResponse::from);
    }

    @Transactional(readOnly = true)
    public List<OperationCaisseResponse> historiqueDuJour(Long caisseId) {
        LocalDateTime debut = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime fin   = debut.plusDays(1);
        return operationRepository
                .findByCaisseIdAndDateOperationBetweenAndAnnuleeFalse(caisseId, debut, fin)
                .stream()
                .map(OperationCaisseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OperationCaisseResponse obtenir(Long id) {
        return OperationCaisseResponse.from(trouver(id));
    }

    private OperationCaisse trouver(Long id) {
        return operationRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Opération", id));
    }

    // ==================================================================
    //  HELPERS
    // ==================================================================

    private static BigDecimal applyMontant(BigDecimal soldeCourant,
                                           TypeOperation type,
                                           BigDecimal montant) {
        return (type == TypeOperation.ENTREE)
                ? soldeCourant.add(montant)
                : soldeCourant.subtract(montant);
    }

    public EnvoiWhatsAppResponse envoyerWhatsApp(Long id, @NotBlank @Size(max = 20) String telephone) {
        return null;
    }
}