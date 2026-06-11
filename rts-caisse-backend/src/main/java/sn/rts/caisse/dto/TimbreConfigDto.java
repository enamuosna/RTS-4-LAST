package sn.rts.caisse.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import sn.rts.caisse.model.ModePaiement;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO bidirectionnel pour la consultation et la mise à jour de la
 * configuration du timbre fiscal.
 *
 * @param actif          active/désactive globalement le timbre
 * @param seuil          montant HT (FCFA) à partir duquel le timbre s'applique
 * @param pourcentage    taux en % du montant HT (ex : 1.00 = 1%)
 * @param categorieIds   catégories concernées ; liste vide = toutes
 * @param modesPaiement  modes de paiement concernés ; liste vide = tous
 */
public record TimbreConfigDto(
        @NotNull boolean actif,
        @NotNull @DecimalMin("0.0") BigDecimal seuil,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal pourcentage,
        List<Long> categorieIds,
        List<ModePaiement> modesPaiement
) {
}
