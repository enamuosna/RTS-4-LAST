package sn.rts.caisse.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ventilation hebdomadaire des recettes d'une caisse sur une période [dateDebut, dateFin].
 *
 * <p>Reproduit la fiche papier RTS : tableau des produits (HT / Timbre / TTC)
 * avec total des recettes, bloc « Fiche de références » (les reçus de la
 * période) et bloc « Reversements des produits » (versements bancaires de la
 * période), plus l'état de double validation (Contrôle 1 / Contrôle 2).</p>
 */
public record VentilationRecetteResponse(
        Long recetteId,
        Long caisseId,
        String caisseCode,
        String caisseLibelle,
        LocalDate dateDebut,
        LocalDate dateFin,
        String statut,
        Long controle1ParId,
        String controle1ParNom,
        LocalDateTime controle1Le,
        Long controle2ParId,
        String controle2ParNom,
        LocalDateTime controle2Le,
        List<LigneVentilation> lignes,
        BigDecimal totalHt,
        BigDecimal totalTimbre,
        BigDecimal totalTtc,
        List<FicheReference> fiches,
        List<ReversementLigne> reversements,
        BigDecimal totalReversements
) {

    /** Une ligne « produit » du tableau de ventilation. */
    public record LigneVentilation(
            String produitCode,
            String produitLibelle,
            BigDecimal montantHt,
            BigDecimal timbre,
            BigDecimal montantTtc
    ) {}

    /** Une entrée du bloc « Fiche de références » (un reçu de la période). */
    public record FicheReference(
            String numeroRecu,
            LocalDateTime date,
            BigDecimal montant
    ) {}

    /** Une entrée du bloc « Reversements des produits » (un versement bancaire). */
    public record ReversementLigne(
            LocalDateTime date,
            String refBordereau,
            BigDecimal montant
    ) {}
}
