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

            // ---------- 3. Calcul du montant TTC (montant + timbre) ----------
            BigDecimal timbre = request.timbre() != null ? request.timbre() : BigDecimal.ZERO;
            BigDecimal montantTtc = request.montant().add(timbre);

            // Solde suffisant pour les sorties (sur le TTC)
            if (request.typeOperation() == TypeOperation.SORTIE
                    && caisse.getSoldeCourant().compareTo(montantTtc) < 0) {
                throw new BusinessException(
                        "Solde insuffisant : solde courant = " + caisse.getSoldeCourant()
                                + " FCFA, montant TTC demandé = " + montantTtc + " FCFA.");
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
                    .timbre(timbre)
                    .montantTtc(montantTtc)
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

            // ---------- 8. Mise à jour du solde (sur le TTC) ----------
            caisse.setSoldeCourant(applyMontant(
                    caisse.getSoldeCourant(),
                    request.typeOperation(),
                    montantTtc));

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

            // Contre-passation sur le TTC (qui est la valeur réellement appliquée au solde)
            BigDecimal ttc = operation.getMontantTtc() != null
                    ? operation.getMontantTtc() : operation.getMontant();
            if (inverse == TypeOperation.SORTIE
                    && caisse.getSoldeCourant().compareTo(ttc) < 0) {
                throw new BusinessException(
                        "Impossible d'annuler : solde courant insuffisant pour contre-passer.");
            }

            caisse.setSoldeCourant(applyMontant(
                    caisse.getSoldeCourant(),
                    inverse,
                    ttc));

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
    //  MODIFICATION (correction d'erreur de saisie)
    // ==================================================================

    /**
     * Modifie une opération existante. Permet à un caissier ou un agent de
     * recettes de corriger une erreur de saisie sans avoir à annuler puis
     * recréer l'opération.
     *
     * <p>Le solde de la caisse est recalculé : on défait l'effet de l'ancien
     * TTC sur le solde, puis on applique le nouveau TTC. L'opération
     * d'origine reste avec le même {@code numeroRecu} et la même
     * {@code dateOperation} ; seuls les champs métier changent.</p>
     *
     * <p>Restrictions :
     * <ul>
     *   <li>Impossible de modifier une opération annulée.</li>
     *   <li>Impossible de modifier une opération déjà rattachée à un journal
     *       clôturé (intégrité comptable).</li>
     *   <li>Le type d'opération ne peut pas être inversé (ENTREE ↔ SORTIE) :
     *       pour ce cas, il faut annuler et resaisir.</li>
     * </ul>
     */
    public OperationCaisseResponse modifier(Long operationId,
                                             OperationCaisseRequest request,
                                             String loginModificateur) {
        OperationCaisse operation;
        try {
            operation = trouver(operationId);
            verifierDroitModifierOuReactiver(operation, loginModificateur);

            if (operation.isAnnulee()) {
                throw new BusinessException(
                        "Impossible de modifier : l'opération est déjà annulée.");
            }
            if (operation.getJournal() != null && operation.getJournal().isCloture()) {
                throw new BusinessException(
                        "Impossible de modifier : la journée de caisse est déjà clôturée.");
            }
            if (operation.getTypeOperation() != request.typeOperation()) {
                throw new BusinessException(
                        "Le type d'opération ne peut pas être modifié. "
                                + "Annulez et resaisissez l'opération si nécessaire.");
            }

            // ---------- Charger les nouvelles relations ----------
            CategorieOperation categorie = categorieService.trouver(request.categorieId());
            if (categorie.getTypeOperation() != request.typeOperation()) {
                throw new BusinessException(
                        "La catégorie '" + categorie.getLibelle()
                                + "' ne correspond pas au type d'opération.");
            }
            if (!categorie.isActif()) {
                throw new BusinessException(
                        "Catégorie désactivée : " + categorie.getLibelle());
            }

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
            }
            Client client = request.clientId() != null
                    ? clientService.trouver(request.clientId())
                    : null;

            // ---------- Recalcul TTC ----------
            BigDecimal nouveauTimbre = request.timbre() != null
                    ? request.timbre() : BigDecimal.ZERO;
            BigDecimal nouveauTtc = request.montant().add(nouveauTimbre);

            // ---------- Recalcul du solde caisse ----------
            // 1) Défait l'ancien effet : si ENTREE on retire l'ancien TTC, sinon on l'ajoute.
            // 2) Applique le nouveau TTC dans le sens du type d'opération.
            Caisse caisse = operation.getCaisse();
            BigDecimal soldeAvant = caisse.getSoldeCourant();
            BigDecimal ancienTtc = operation.getMontantTtc() != null
                    ? operation.getMontantTtc() : operation.getMontant();

            BigDecimal soldeApresAnnulation = applyMontant(
                    soldeAvant,
                    inverseType(operation.getTypeOperation()),
                    ancienTtc);

            if (request.typeOperation() == TypeOperation.SORTIE
                    && soldeApresAnnulation.compareTo(nouveauTtc) < 0) {
                throw new BusinessException(
                        "Solde insuffisant après recalcul : disponible "
                                + soldeApresAnnulation + " FCFA, requis " + nouveauTtc + " FCFA.");
            }

            BigDecimal nouveauSolde = applyMontant(
                    soldeApresAnnulation,
                    request.typeOperation(),
                    nouveauTtc);
            caisse.setSoldeCourant(nouveauSolde);

            // ---------- Mise à jour des champs ----------
            operation.setMontant(request.montant());
            operation.setTimbre(nouveauTimbre);
            operation.setMontantTtc(nouveauTtc);
            operation.setMotif(request.motif());
            operation.setModePaiement(mode);
            operation.setReference(request.reference());
            operation.setCategorie(categorie);
            operation.setClient(client);
            operation.setBanque(banque);

            log.info("Opération {} modifiée par {} : montant={} timbre={} TTC={} solde {}->{}",
                    operation.getNumeroRecu(), loginModificateur,
                    operation.getMontant(), operation.getTimbre(), operation.getMontantTtc(),
                    soldeAvant, nouveauSolde);

            auditService.logSuccess(
                    AuditAction.MODIFIER_OPERATION,
                    "OperationCaisse",
                    operation.getId(),
                    operation.getNumeroRecu(),
                    "Type=" + operation.getTypeOperation()
                            + " Montant=" + operation.getMontant() + " FCFA"
                            + " Timbre=" + operation.getTimbre() + " FCFA"
                            + " TTC=" + operation.getMontantTtc() + " FCFA"
                            + " Mode=" + operation.getModePaiement()
                            + " Caisse=" + caisse.getCode()
                            + " SoldeAvant=" + soldeAvant + " FCFA"
                            + " SoldeApres=" + nouveauSolde + " FCFA"
                            + " ModifieePar=" + loginModificateur);

            return OperationCaisseResponse.from(operation);

        } catch (BusinessException | ResourceNotFoundException e) {
            auditService.logFailure(
                    AuditAction.MODIFIER_OPERATION,
                    "OperationCaisse",
                    operationId,
                    "par=" + loginModificateur,
                    e.getMessage());
            throw e;
        }
    }

    private static TypeOperation inverseType(TypeOperation t) {
        return t == TypeOperation.ENTREE ? TypeOperation.SORTIE : TypeOperation.ENTREE;
    }

    /**
     * Vérifie que l'utilisateur a le droit de <b>modifier</b> ou
     * <b>réactiver</b> une opération. Autorisé pour :
     * <ul>
     *   <li>{@link sn.rts.caisse.model.Role#ADMIN} et
     *       {@link sn.rts.caisse.model.Role#SUPERVISEUR} : sur toutes
     *       les opérations</li>
     *   <li>{@link sn.rts.caisse.model.Role#AGENT_RECETTE} : uniquement
     *       sur les opérations de la caisse à laquelle il est affecté</li>
     * </ul>
     * Les CAISSIERS ne peuvent pas modifier ni réactiver — ils ne peuvent
     * qu'annuler.
     */
    private void verifierDroitModifierOuReactiver(OperationCaisse operation, String login) {
        Utilisateur user = utilisateurRepository.findByLogin(login)
                .orElseThrow(() -> new BusinessException(
                        "Utilisateur introuvable : " + login));

        sn.rts.caisse.model.Role role = user.getRole();
        if (role == sn.rts.caisse.model.Role.ADMIN
                || role == sn.rts.caisse.model.Role.SUPERVISEUR) {
            return; // OK, accès global
        }
        if (role == sn.rts.caisse.model.Role.AGENT_RECETTE) {
            Caisse caisse = operation.getCaisse();
            Utilisateur agent = caisse.getAgentRecette();
            if (agent != null && user.getId().equals(agent.getId())) {
                return; // OK, agent affecté à cette caisse
            }
            throw new BusinessException(
                    "Vous n'êtes pas l'agent de recette affecté à cette caisse.");
        }
        throw new BusinessException(
                "Action réservée aux agents de recette, superviseurs et administrateurs.");
    }

    // ==================================================================
    //  RÉACTIVATION (annule la contre-passation)
    // ==================================================================

    /**
     * Défait une annulation : l'opération repasse à {@code annulee=false}
     * et son TTC est ré-appliqué au solde de la caisse comme si elle n'avait
     * jamais été annulée. Utile quand un caissier annule une opération par
     * erreur — l'agent de recette peut alors la réactiver sans perdre les
     * données ni le numéro de reçu.
     *
     * <p>Réservé aux ADMIN, SUPERVISEUR et à l'AGENT_RECETTE de la caisse.
     * Refusé si la journée est clôturée.</p>
     */
    public OperationCaisseResponse reactiver(Long operationId, String loginReactivateur) {
        OperationCaisse operation;
        try {
            operation = trouver(operationId);
            verifierDroitModifierOuReactiver(operation, loginReactivateur);

            if (!operation.isAnnulee()) {
                throw new BusinessException(
                        "L'opération n'est pas annulée, rien à réactiver.");
            }
            if (operation.getJournal() != null && operation.getJournal().isCloture()) {
                throw new BusinessException(
                        "Impossible de réactiver : la journée de caisse est déjà clôturée.");
            }

            Caisse caisse = operation.getCaisse();
            BigDecimal soldeAvant = caisse.getSoldeCourant();
            BigDecimal ttc = operation.getMontantTtc() != null
                    ? operation.getMontantTtc() : operation.getMontant();

            // Si l'op était une SORTIE annulée, son annulation a re-crédité
            // la caisse (contre-passation). On la re-débite en réactivant.
            // Inversement pour une ENTREE annulée.
            if (operation.getTypeOperation() == TypeOperation.SORTIE
                    && caisse.getSoldeCourant().compareTo(ttc) < 0) {
                throw new BusinessException(
                        "Solde insuffisant pour réactiver cette sortie : disponible "
                                + caisse.getSoldeCourant() + " FCFA, requis " + ttc + " FCFA.");
            }

            caisse.setSoldeCourant(applyMontant(
                    caisse.getSoldeCourant(),
                    operation.getTypeOperation(),
                    ttc));

            operation.setAnnulee(false);
            operation.setMotifAnnulation(null);

            log.info("Opération {} réactivée par {} : solde {}->{}",
                    operation.getNumeroRecu(), loginReactivateur,
                    soldeAvant, caisse.getSoldeCourant());

            auditService.logSuccess(
                    AuditAction.REACTIVER_OPERATION,
                    "OperationCaisse",
                    operation.getId(),
                    operation.getNumeroRecu(),
                    "Type=" + operation.getTypeOperation()
                            + " Montant=" + operation.getMontant() + " FCFA"
                            + " TTC=" + ttc + " FCFA"
                            + " Caisse=" + caisse.getCode()
                            + " SoldeAvant=" + soldeAvant + " FCFA"
                            + " SoldeApres=" + caisse.getSoldeCourant() + " FCFA"
                            + " ReactiveePar=" + loginReactivateur);

            return OperationCaisseResponse.from(operation);

        } catch (BusinessException | ResourceNotFoundException e) {
            auditService.logFailure(
                    AuditAction.REACTIVER_OPERATION,
                    "OperationCaisse",
                    operationId,
                    "par=" + loginReactivateur,
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

    /**
     * Opérations de la <b>session de caisse en cours</b> (journal pas encore
     * clôturé). Utilisé par le guichet JavaFX : tant que la caisse est ouverte
     * le caissier voit ses opérations, après clôture la liste est vide côté
     * guichet — les opérations restent consultables côté admin (web) via
     * l'historique paginé.
     */
    @Transactional(readOnly = true)
    public List<OperationCaisseResponse> historiqueSessionCourante(Long caisseId) {
        return operationRepository
                .findByCaisseIdAndJournalIsNullAndAnnuleeFalseOrderByDateOperationDesc(caisseId)
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