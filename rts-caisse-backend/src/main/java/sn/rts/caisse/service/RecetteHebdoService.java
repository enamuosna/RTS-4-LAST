package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.audit.AuditAction;
import sn.rts.caisse.audit.AuditService;
import sn.rts.caisse.dto.ConsolidationRecetteResponse;
import sn.rts.caisse.dto.ConsolidationRecetteResponse.LigneCaisse;
import sn.rts.caisse.dto.VentilationRecetteResponse;
import sn.rts.caisse.dto.VentilationRecetteResponse.FicheReference;
import sn.rts.caisse.dto.VentilationRecetteResponse.LigneVentilation;
import sn.rts.caisse.dto.VentilationRecetteResponse.ReversementLigne;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.*;
import sn.rts.caisse.repository.OperationCaisseRepository;
import sn.rts.caisse.repository.RecetteHebdoRepository;
import sn.rts.caisse.repository.UtilisateurRepository;
import sn.rts.caisse.repository.VersementRepository;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ventilation hebdomadaire des recettes par caisse, avec double validation.
 *
 * <p>Les montants sont <b>recalculés à la volée</b> depuis les opérations
 * (encaissements non annulés) et les versements de la période. Seul l'état de
 * validation (qui a signé quel contrôle, et quand) est persisté dans
 * {@link RecetteHebdo}. La première consultation d'une période crée la ligne au
 * statut {@link StatutRecette#BROUILLON}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RecetteHebdoService {

    private final RecetteHebdoRepository recetteRepository;
    private final OperationCaisseRepository operationRepository;
    private final VersementRepository versementRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CaisseService caisseService;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    //  Génération / consultation
    // ------------------------------------------------------------------

    /** Calcule la ventilation pour une caisse + période ; crée le brouillon si absent. */
    public VentilationRecetteResponse ventilation(Long caisseId, LocalDate dateDebut, LocalDate dateFin) {
        Caisse caisse = caisseService.trouver(caisseId);
        LocalDate[] bornes = normaliser(dateDebut, dateFin);
        LocalDate dd = bornes[0];
        LocalDate df = bornes[1];

        RecetteHebdo rec = recetteRepository
                .findByCaisseIdAndDateDebutAndDateFin(caisseId, dd, df)
                .orElseGet(() -> recetteRepository.save(RecetteHebdo.builder()
                        .caisse(caisse)
                        .dateDebut(dd)
                        .dateFin(df)
                        .statut(StatutRecette.BROUILLON)
                        .build()));

        return construire(rec);
    }

    @Transactional(readOnly = true)
    public VentilationRecetteResponse obtenirParId(Long id) {
        return construire(trouver(id));
    }

    @Transactional(readOnly = true)
    public Page<VentilationRecetteResponse> historique(Long caisseId, Pageable pageable) {
        Page<RecetteHebdo> page = caisseId != null
                ? recetteRepository.findByCaisseIdOrderByDateDebutDesc(caisseId, pageable)
                : recetteRepository.findAllByOrderByDateDebutDesc(pageable);
        return page.map(this::construire);
    }

    /**
     * Consolidation de TOUTES les caisses sur une période : ventilation
     * globale par produit + répartition par caisse. Lecture seule (aucune
     * persistance), pour le pilotage direction.
     */
    @Transactional(readOnly = true)
    public ConsolidationRecetteResponse consolidation(LocalDate dateDebut, LocalDate dateFin) {
        LocalDate[] bornes = normaliser(dateDebut, dateFin);
        LocalDateTime debut = bornes[0].atStartOfDay();
        LocalDateTime fin = bornes[1].atTime(LocalTime.MAX);

        List<OperationCaisse> ops = operationRepository.findByDateOperationBetween(debut, fin);

        Map<Long, BigDecimal[]> parProduit = new HashMap<>(); // [HT, Timbre, TTC]
        Map<Long, String[]> nomsProduit = new HashMap<>();    // [code, libelle]
        Map<Long, BigDecimal[]> parCaisse = new HashMap<>();  // [HT, Timbre, TTC]
        Map<Long, String[]> nomsCaisse = new HashMap<>();     // [code, libelle]
        Map<Long, Long> nbParCaisse = new HashMap<>();

        for (OperationCaisse o : ops) {
            if (o.isAnnulee() || o.getTypeOperation() != TypeOperation.ENTREE) continue;
            BigDecimal ht = nz(o.getMontant());
            BigDecimal tb = nz(o.getTimbre());
            BigDecimal ttc = o.getMontantTtc() != null ? o.getMontantTtc() : ht.add(tb);

            CategorieOperation cat = o.getCategorie();
            BigDecimal[] sp = parProduit.computeIfAbsent(cat.getId(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            sp[0] = sp[0].add(ht); sp[1] = sp[1].add(tb); sp[2] = sp[2].add(ttc);
            nomsProduit.putIfAbsent(cat.getId(), new String[]{cat.getCode(), cat.getLibelle()});

            Caisse c = o.getCaisse();
            BigDecimal[] sc = parCaisse.computeIfAbsent(c.getId(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            sc[0] = sc[0].add(ht); sc[1] = sc[1].add(tb); sc[2] = sc[2].add(ttc);
            nomsCaisse.putIfAbsent(c.getId(), new String[]{c.getCode(), c.getLibelle()});
            nbParCaisse.merge(c.getId(), 1L, Long::sum);
        }

        List<LigneVentilation> lignesProduit = parProduit.entrySet().stream()
                .map(e -> new LigneVentilation(
                        nomsProduit.get(e.getKey())[0], nomsProduit.get(e.getKey())[1],
                        e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .sorted(Comparator.comparing(LigneVentilation::produitLibelle, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<LigneCaisse> lignesCaisse = parCaisse.entrySet().stream()
                .map(e -> new LigneCaisse(
                        e.getKey(), nomsCaisse.get(e.getKey())[0], nomsCaisse.get(e.getKey())[1],
                        e.getValue()[0], e.getValue()[1], e.getValue()[2],
                        nbParCaisse.getOrDefault(e.getKey(), 0L)))
                .sorted(Comparator.comparing(LigneCaisse::caisseCode, String.CASE_INSENSITIVE_ORDER))
                .toList();

        BigDecimal totalHt = lignesProduit.stream().map(LigneVentilation::montantHt).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTimbre = lignesProduit.stream().map(LigneVentilation::timbre).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTtc = lignesProduit.stream().map(LigneVentilation::montantTtc).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ConsolidationRecetteResponse(
                bornes[0], bornes[1], lignesProduit, totalHt, totalTimbre, totalTtc, lignesCaisse);
    }

    // ------------------------------------------------------------------
    //  Double validation
    // ------------------------------------------------------------------

    /** Contrôle 1 — Chef Unité Finances. */
    public VentilationRecetteResponse controle1(Long id, String login) {
        try {
            RecetteHebdo rec = trouver(id);
            if (rec.getStatut() != StatutRecette.BROUILLON) {
                throw new BusinessException(
                        "Le Contrôle 1 a déjà été effectué ou la fiche est déjà validée.");
            }
            Utilisateur u = utilisateurRepository.findByLogin(login)
                    .orElseThrow(() -> new BusinessException("Utilisateur introuvable : " + login));

            rec.setControle1Par(u);
            rec.setControle1Le(LocalDateTime.now());
            rec.setStatut(StatutRecette.CONTROLE_1);

            auditService.logSuccess(AuditAction.CONTROLE_1_RECETTE, "RecetteHebdo", rec.getId(),
                    libelle(rec), details(rec) + " Controle1=" + u.getLogin());
            return construire(rec);

        } catch (BusinessException | ResourceNotFoundException e) {
            auditService.logFailure(AuditAction.CONTROLE_1_RECETTE, "RecetteHebdo", id,
                    "user=" + login, e.getMessage());
            throw e;
        }
    }

    /** Contrôle 2 — Chef de Département (final). Signataire ≠ Contrôle 1. */
    public VentilationRecetteResponse controle2(Long id, String login) {
        try {
            RecetteHebdo rec = trouver(id);
            if (rec.getStatut() == StatutRecette.BROUILLON) {
                throw new BusinessException(
                        "Le Contrôle 1 (Chef Unité Finances) doit être effectué avant le Contrôle 2.");
            }
            if (rec.getStatut() == StatutRecette.VALIDEE) {
                throw new BusinessException("Cette ventilation est déjà validée.");
            }
            Utilisateur u = utilisateurRepository.findByLogin(login)
                    .orElseThrow(() -> new BusinessException("Utilisateur introuvable : " + login));

            if (rec.getControle1Par() != null && rec.getControle1Par().getId().equals(u.getId())) {
                throw new BusinessException(
                        "Le Contrôle 2 doit être réalisé par une personne différente du Contrôle 1.");
            }

            rec.setControle2Par(u);
            rec.setControle2Le(LocalDateTime.now());
            rec.setStatut(StatutRecette.VALIDEE);

            auditService.logSuccess(AuditAction.CONTROLE_2_RECETTE, "RecetteHebdo", rec.getId(),
                    libelle(rec), details(rec) + " Controle2=" + u.getLogin());
            return construire(rec);

        } catch (BusinessException | ResourceNotFoundException e) {
            auditService.logFailure(AuditAction.CONTROLE_2_RECETTE, "RecetteHebdo", id,
                    "user=" + login, e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------
    //  Calcul de la ventilation (à la volée)
    // ------------------------------------------------------------------

    private VentilationRecetteResponse construire(RecetteHebdo rec) {
        Long caisseId = rec.getCaisse().getId();
        LocalDateTime debut = rec.getDateDebut().atStartOfDay();
        LocalDateTime fin = rec.getDateFin().atTime(LocalTime.MAX);

        List<OperationCaisse> ops = operationRepository
                .findByCaisseIdAndDateOperationBetweenAndAnnuleeFalse(caisseId, debut, fin);

        // Agrégation par produit (recettes = ENTREE uniquement)
        Map<Long, BigDecimal[]> sommes = new HashMap<>();  // [HT, Timbre, TTC]
        Map<Long, String[]> noms = new HashMap<>();         // [code, libelle]
        List<FicheReference> fiches = new ArrayList<>();

        for (OperationCaisse o : ops) {
            if (o.getTypeOperation() != TypeOperation.ENTREE) continue;
            CategorieOperation cat = o.getCategorie();
            Long cid = cat.getId();
            BigDecimal ht = nz(o.getMontant());
            BigDecimal tb = nz(o.getTimbre());
            BigDecimal ttc = o.getMontantTtc() != null ? o.getMontantTtc() : ht.add(tb);

            BigDecimal[] s = sommes.computeIfAbsent(cid,
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            s[0] = s[0].add(ht);
            s[1] = s[1].add(tb);
            s[2] = s[2].add(ttc);
            noms.putIfAbsent(cid, new String[]{cat.getCode(), cat.getLibelle()});

            fiches.add(new FicheReference(o.getNumeroRecu(), o.getDateOperation(), ttc));
        }

        List<LigneVentilation> lignes = sommes.entrySet().stream()
                .map(e -> new LigneVentilation(
                        noms.get(e.getKey())[0],
                        noms.get(e.getKey())[1],
                        e.getValue()[0],
                        e.getValue()[1],
                        e.getValue()[2]))
                .sorted(Comparator.comparing(LigneVentilation::produitLibelle, String.CASE_INSENSITIVE_ORDER))
                .toList();

        BigDecimal totalHt = lignes.stream().map(LigneVentilation::montantHt).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTimbre = lignes.stream().map(LigneVentilation::timbre).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTtc = lignes.stream().map(LigneVentilation::montantTtc).reduce(BigDecimal.ZERO, BigDecimal::add);

        fiches.sort(Comparator.comparing(FicheReference::date));

        // Reversements de la période
        List<Versement> vers = versementRepository
                .findByCaisseIdAndDateVersementBetweenOrderByDateVersementAsc(caisseId, debut, fin);
        List<ReversementLigne> reversements = vers.stream()
                .map(v -> new ReversementLigne(v.getDateVersement(), v.getNumeroBordereau(), nz(v.getMontant())))
                .toList();
        BigDecimal totalReversements = vers.stream()
                .map(v -> nz(v.getMontant())).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new VentilationRecetteResponse(
                rec.getId(),
                caisseId,
                rec.getCaisse().getCode(),
                rec.getCaisse().getLibelle(),
                rec.getDateDebut(),
                rec.getDateFin(),
                rec.getStatut().name(),
                rec.getControle1Par() != null ? rec.getControle1Par().getId() : null,
                rec.getControle1Par() != null ? rec.getControle1Par().getNomComplet() : null,
                rec.getControle1Le(),
                rec.getControle2Par() != null ? rec.getControle2Par().getId() : null,
                rec.getControle2Par() != null ? rec.getControle2Par().getNomComplet() : null,
                rec.getControle2Le(),
                lignes, totalHt, totalTimbre, totalTtc,
                fiches, reversements, totalReversements);
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private RecetteHebdo trouver(Long id) {
        return recetteRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("RecetteHebdo", id));
    }

    /** Bornes par défaut : semaine courante (lundi → dimanche) si aucune fournie. */
    private LocalDate[] normaliser(LocalDate dd, LocalDate df) {
        LocalDate aujourd = LocalDate.now();
        if (dd == null && df == null) {
            dd = aujourd.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            df = dd.plusDays(6);
        } else if (dd == null) {
            dd = df;
        } else if (df == null) {
            df = dd;
        }
        if (df.isBefore(dd)) {
            LocalDate t = dd; dd = df; df = t;
        }
        return new LocalDate[]{dd, df};
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String libelle(RecetteHebdo r) {
        return r.getCaisse().getCode() + " — " + r.getDateDebut() + " au " + r.getDateFin();
    }

    private static String details(RecetteHebdo r) {
        return "Caisse=" + r.getCaisse().getCode()
                + " Periode=" + r.getDateDebut() + ".." + r.getDateFin()
                + " Statut=" + r.getStatut();
    }
}
