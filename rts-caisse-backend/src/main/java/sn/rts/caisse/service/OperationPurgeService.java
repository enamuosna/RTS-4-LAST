package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.audit.AuditAction;
import sn.rts.caisse.audit.AuditService;
import sn.rts.caisse.dto.PurgeFilter;
import sn.rts.caisse.dto.PurgePreviewResponse;
import sn.rts.caisse.dto.PurgeResult;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.Caisse;
import sn.rts.caisse.model.JournalCaisse;
import sn.rts.caisse.model.OperationCaisse;
import sn.rts.caisse.model.TypeOperation;
import sn.rts.caisse.repository.OperationCaisseRepository;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service de purge des opérations de caisse — réservé aux ADMIN.
 *
 * <h2>Pourquoi un service séparé</h2>
 * <ul>
 *   <li>Garde {@link OperationCaisseService} (~800 lignes) concentré sur
 *       les opérations métier (création, annulation soft, modification).</li>
 *   <li>Isole la logique sensible de suppression définitive avec ses propres
 *       garanties d'audit et de cohérence des soldes.</li>
 * </ul>
 *
 * <h2>Règles métier appliquées par opération supprimée</h2>
 * <ol>
 *   <li><b>Op annulée</b> → simple {@code DELETE}. Le solde a déjà été
 *       contre-passé au moment de l'annulation, rien à refaire.</li>
 *   <li><b>Op active, journal NULL ou non clôturé</b> → contre-passation
 *       automatique du solde de la caisse, puis {@code DELETE}. L'effet
 *       initial sur le solde est annulé pour préserver la cohérence.</li>
 *   <li><b>Op active, journal clôturé</b> → {@code DELETE} sans contre-pass.
 *       Le snapshot du journal est figé (totaux historiques inchangés). Le
 *       solde courant de la caisse n'est pas modifié non plus car il
 *       reflète des journées postérieures.</li>
 * </ol>
 *
 * <h2>Audit</h2>
 * Chaque suppression produit une ligne {@link AuditAction#SUPPRIMER_OPERATION_DEFINITIVEMENT}
 * dans {@code audit_logs} avec tous les détails (n° reçu, montant, mode,
 * caisse, login de l'admin auteur, état avant suppression).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationPurgeService {

    /** Plafond de sécurité : refuse une purge en masse au-delà de ce volume. */
    public static final int LIMITE_PURGE_BULK = 5_000;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final OperationCaisseRepository repository;
    private final AuditService              auditService;

    // ==================================================================
    //  PRÉVISUALISATION (lecture seule, calcule l'impact)
    // ==================================================================

    @Transactional(readOnly = true)
    public PurgePreviewResponse previewPurge(PurgeFilter filter) {
        List<OperationCaisse> candidats = chercherCandidats(filter);
        if (candidats.isEmpty()) {
            return new PurgePreviewResponse(0, 0, 0, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        }
        long nbAnnulees = candidats.stream().filter(OperationCaisse::isAnnulee).count();
        long nbActives  = candidats.size() - nbAnnulees;
        long nbDansCloture = candidats.stream()
                .filter(op -> !op.isAnnulee())
                .filter(op -> op.getJournal() != null && op.getJournal().isCloture())
                .count();
        BigDecimal sommeEntrees = candidats.stream()
                .filter(op -> !op.isAnnulee())
                .filter(op -> op.getTypeOperation() == TypeOperation.ENTREE)
                .map(OperationPurgeService::ttc)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sommeSorties = candidats.stream()
                .filter(op -> !op.isAnnulee())
                .filter(op -> op.getTypeOperation() == TypeOperation.SORTIE)
                .map(OperationPurgeService::ttc)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime plusAncienne = candidats.get(0).getDateOperation();
        LocalDateTime plusRecente  = candidats.get(candidats.size() - 1).getDateOperation();
        return new PurgePreviewResponse(candidats.size(), nbAnnulees, nbActives,
                nbDansCloture, sommeEntrees, sommeSorties, plusAncienne, plusRecente);
    }

    // ==================================================================
    //  EXPORT CSV (snapshot avant purge - traçabilité hors base)
    // ==================================================================

    @Transactional(readOnly = true)
    public byte[] exportCsv(PurgeFilter filter) {
        List<OperationCaisse> candidats = chercherCandidats(filter);
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            // BOM UTF-8 pour Excel
            w.write('﻿');
            w.println("id;numero_recu;date_operation;caisse_code;caisse_libelle;type;categorie;"
                    + "mode_paiement;montant;timbre;montant_ttc;motif;client;caissier;"
                    + "annulee;motif_annulation;journal_id;journal_cloture");
            for (OperationCaisse op : candidats) {
                w.print(op.getId());                     w.print(';');
                w.print(csv(op.getNumeroRecu()));        w.print(';');
                w.print(op.getDateOperation() == null ? "" : ISO.format(op.getDateOperation())); w.print(';');
                w.print(csv(op.getCaisse() == null ? null : op.getCaisse().getCode()));    w.print(';');
                w.print(csv(op.getCaisse() == null ? null : op.getCaisse().getLibelle())); w.print(';');
                w.print(op.getTypeOperation());          w.print(';');
                w.print(csv(op.getCategorie() == null ? null : op.getCategorie().getLibelle())); w.print(';');
                w.print(op.getModePaiement());           w.print(';');
                w.print(op.getMontant());                w.print(';');
                w.print(op.getTimbre());                 w.print(';');
                w.print(op.getMontantTtc());             w.print(';');
                w.print(csv(op.getMotif()));             w.print(';');
                w.print(csv(op.getClient() == null ? null : op.getClient().getRaisonSociale())); w.print(';');
                w.print(csv(op.getCaissier() == null ? null : op.getCaissier().getLogin())); w.print(';');
                w.print(op.isAnnulee());                 w.print(';');
                w.print(csv(op.getMotifAnnulation()));   w.print(';');
                w.print(op.getJournal() == null ? "" : op.getJournal().getId()); w.print(';');
                w.println(op.getJournal() != null && op.getJournal().isCloture());
            }
        }
        return out.toByteArray();
    }

    // ==================================================================
    //  PURGE EN MASSE
    // ==================================================================

    /**
     * Purge tous les candidats du filtre. Chaque suppression est faite dans
     * sa propre transaction (REQUIRES_NEW) pour qu'un échec sur une op ne
     * fasse pas rollback les précédentes. Au-dessus de {@link #LIMITE_PURGE_BULK}
     * candidats, refuse pour éviter une opération destructive géante.
     */
    @Transactional(readOnly = true)
    public PurgeResult purgerEnMasse(PurgeFilter filter, String loginAdmin) {
        List<OperationCaisse> candidats = chercherCandidats(filter);
        if (candidats.size() > LIMITE_PURGE_BULK) {
            throw new BusinessException(
                    "Trop d'opérations à purger (" + candidats.size()
                            + "). Limite : " + LIMITE_PURGE_BULK
                            + ". Affinez les filtres (date, caisse).");
        }
        // On extrait les IDs pour ne pas tenir d'objets JPA détachés entre tx.
        List<Long> ids = candidats.stream().map(OperationCaisse::getId).toList();

        int supprimees = 0, contrepassees = 0, echecs = 0;
        List<String> erreurs = new ArrayList<>();
        for (Long id : ids) {
            try {
                boolean contrePass = supprimerUneOpEnNouvelleTx(id, loginAdmin);
                supprimees++;
                if (contrePass) contrepassees++;
            } catch (Exception e) {
                echecs++;
                if (erreurs.size() < 10) {
                    erreurs.add("op #" + id + " : " + e.getMessage());
                }
                log.warn("Echec purge op #{} par {} : {}", id, loginAdmin, e.getMessage());
            }
        }
        log.info("Purge en masse par {} : {} supprimées, {} contre-passées, {} échecs",
                loginAdmin, supprimees, contrepassees, echecs);
        return new PurgeResult(supprimees, contrepassees, echecs, erreurs);
    }

    // ==================================================================
    //  PURGE UNITAIRE (depuis bouton corbeille admin sur une ligne)
    // ==================================================================

    public PurgeResult supprimerUneOp(Long id, String loginAdmin) {
        try {
            boolean contrePass = supprimerUneOpEnNouvelleTx(id, loginAdmin);
            return new PurgeResult(1, contrePass ? 1 : 0, 0, List.of());
        } catch (BusinessException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Echec purge op #{} par {}", id, loginAdmin, e);
            return new PurgeResult(0, 0, 1, List.of("op #" + id + " : " + e.getMessage()));
        }
    }

    // ==================================================================
    //  Coeur de la suppression - une transaction par opération pour
    //  l'isolation des échecs en bulk.
    // ==================================================================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean supprimerUneOpEnNouvelleTx(Long id, String loginAdmin) {
        OperationCaisse op = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("OperationCaisse", id));

        // Capture du contexte avant suppression (pour le log d'audit).
        String numeroRecu  = op.getNumeroRecu();
        TypeOperation type = op.getTypeOperation();
        BigDecimal montant = op.getMontant();
        BigDecimal ttc     = ttc(op);
        Caisse caisse      = op.getCaisse();
        String caisseCode  = caisse == null ? "?" : caisse.getCode();
        JournalCaisse j    = op.getJournal();
        boolean journalCloture = j != null && j.isCloture();
        boolean etaitAnnulee   = op.isAnnulee();
        BigDecimal soldeAvant  = caisse == null ? null : caisse.getSoldeCourant();

        boolean contrepasseEffectue = false;
        BigDecimal soldeApres = soldeAvant;

        if (!etaitAnnulee && !journalCloture && caisse != null) {
            // Contre-passation : inverse l'effet initial sur le solde caisse.
            TypeOperation inverse = (type == TypeOperation.ENTREE)
                    ? TypeOperation.SORTIE : TypeOperation.ENTREE;
            if (inverse == TypeOperation.SORTIE
                    && caisse.getSoldeCourant().compareTo(ttc) < 0) {
                throw new BusinessException(
                        "Impossible de supprimer : la contre-passation rendrait "
                                + "le solde de la caisse " + caisseCode + " négatif "
                                + "(" + caisse.getSoldeCourant() + " - " + ttc + " FCFA).");
            }
            caisse.setSoldeCourant(applySolde(caisse.getSoldeCourant(), inverse, ttc));
            soldeApres = caisse.getSoldeCourant();
            contrepasseEffectue = true;
        }

        repository.delete(op);

        // Audit : on logue APRÈS le delete (si le delete plante, la tx
        // rollback et l'entrée d'audit n'est pas écrite — c'est OK car
        // AuditService écrit lui-même en REQUIRES_NEW de toute façon).
        String details = "Type=" + type
                + " Montant=" + montant + " FCFA"
                + " TTC=" + ttc + " FCFA"
                + " Caisse=" + caisseCode
                + " EtaitAnnulee=" + etaitAnnulee
                + " JournalCloture=" + journalCloture
                + " ContrePassee=" + contrepasseEffectue
                + (soldeAvant != null ? " SoldeAvant=" + soldeAvant : "")
                + (contrepasseEffectue ? " SoldeApres=" + soldeApres : "")
                + " ParAdmin=" + loginAdmin;
        auditService.logSuccess(
                AuditAction.SUPPRIMER_OPERATION_DEFINITIVEMENT,
                "OperationCaisse", id, numeroRecu, details);

        log.info("Op {} (#{}) supprimée définitivement par {} (contre-pass={})",
                numeroRecu, id, loginAdmin, contrepasseEffectue);
        return contrepasseEffectue;
    }

    // ==================================================================
    //  HELPERS
    // ==================================================================

    private List<OperationCaisse> chercherCandidats(PurgeFilter filter) {
        if (filter == null || filter.avantDate() == null) {
            throw new BusinessException("Le filtre 'avantDate' est obligatoire.");
        }
        LocalDateTime avant = filter.avantDate().atStartOfDay();
        // Refuse une purge dans le futur (garde-fou : avantDate ne doit pas être
        // demain pour ne pas embarquer les opérations du jour par erreur).
        if (filter.avantDate().isAfter(LocalDate.now())) {
            throw new BusinessException(
                    "La date de purge ne peut pas être dans le futur.");
        }
        Boolean annulee = switch (filter.statut()) {
            case TOUS -> null;
            case SEULEMENT_ANNULEES -> Boolean.TRUE;
            case SEULEMENT_ACTIVES  -> Boolean.FALSE;
        };
        return repository.rechercherCandidatsPurge(avant, filter.caisseId(), annulee);
    }

    private static BigDecimal ttc(OperationCaisse op) {
        return op.getMontantTtc() != null ? op.getMontantTtc() : op.getMontant();
    }

    private static BigDecimal applySolde(BigDecimal solde, TypeOperation t, BigDecimal m) {
        return t == TypeOperation.ENTREE ? solde.add(m) : solde.subtract(m);
    }

    private static String csv(String s) {
        if (s == null) return "";
        // Quote si contient un ; ou ou \n ou "
        boolean needQuote = s.indexOf(';') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        String escaped = s.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }
}
