package sn.rts.caisse.dto;

import sn.rts.caisse.dto.VentilationRecetteResponse.LigneVentilation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Consolidation des recettes de <b>toutes les caisses</b> sur une période :
 * ventilation globale par produit + répartition par caisse.
 */
public record ConsolidationRecetteResponse(
        LocalDate dateDebut,
        LocalDate dateFin,
        List<LigneVentilation> parProduit,
        BigDecimal totalHt,
        BigDecimal totalTimbre,
        BigDecimal totalTtc,
        List<LigneCaisse> parCaisse
) {

    /** Sous-total des recettes d'une caisse sur la période. */
    public record LigneCaisse(
            Long caisseId,
            String caisseCode,
            String caisseLibelle,
            BigDecimal totalHt,
            BigDecimal totalTimbre,
            BigDecimal totalTtc,
            long nbOperations
    ) {}
}
