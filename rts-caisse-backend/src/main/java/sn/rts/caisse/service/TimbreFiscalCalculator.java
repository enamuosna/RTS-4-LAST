package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import sn.rts.caisse.model.ModePaiement;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcul automatique du timbre fiscal appliqué aux opérations de caisse RTS.
 *
 * <h2>Règle métier — désormais PERSONNALISABLE</h2>
 * Les paramètres (seuil, taux, catégories concernées, modes de paiement
 * concernés, activation) ne sont plus codés en dur : ils proviennent de la
 * configuration {@link TimbreConfigService} modifiable par l'ADMIN via
 * {@code /api/parametres/timbre}. Le timbre s'applique lorsque :
 * <ul>
 *   <li>la configuration est active, ET</li>
 *   <li>{@code montant >= seuil}, ET</li>
 *   <li>le mode de paiement est concerné (liste vide = tous), ET</li>
 *   <li>la catégorie est concernée (liste vide = toutes).</li>
 * </ul>
 * Le timbre vaut alors {@code montant * pourcentage / 100}, arrondi au FCFA.
 *
 * <p>Le calcul est <b>autoritatif côté backend</b> : on ignore délibérément
 * toute valeur {@code timbre} envoyée par le client. Le guichet lit la même
 * configuration pour afficher un aperçu cohérent.</p>
 */
@Component
@RequiredArgsConstructor
public class TimbreFiscalCalculator {

    private static final BigDecimal CENT = new BigDecimal("100");

    private final TimbreConfigService configService;

    /**
     * Calcule le timbre fiscal pour un montant HT, un mode de paiement et une
     * catégorie donnés, en appliquant la configuration courante.
     *
     * @param montantHt   montant hors taxe en FCFA
     * @param mode        mode de paiement de l'opération
     * @param categorieId identifiant de la catégorie/produit de l'opération
     * @return timbre arrondi au FCFA, ou 0 si non applicable
     */
    public BigDecimal calculer(BigDecimal montantHt, ModePaiement mode, Long categorieId) {
        if (montantHt == null) {
            return BigDecimal.ZERO;
        }
        TimbreConfigService.Reglement r = configService.obtenirReglement();

        if (!r.actif()) {
            return BigDecimal.ZERO;
        }
        if (r.seuil() != null && montantHt.compareTo(r.seuil()) < 0) {
            return BigDecimal.ZERO;
        }
        // Liste de modes vide = tous les modes concernés.
        if (!r.modesPaiement().isEmpty() && !r.modesPaiement().contains(mode)) {
            return BigDecimal.ZERO;
        }
        // Liste de catégories vide = toutes les catégories concernées.
        if (!r.categorieIds().isEmpty()
                && (categorieId == null || !r.categorieIds().contains(categorieId))) {
            return BigDecimal.ZERO;
        }
        BigDecimal taux = r.pourcentage() == null ? BigDecimal.ZERO : r.pourcentage();
        return montantHt.multiply(taux)
                .divide(CENT, 0, RoundingMode.HALF_UP);
    }
}
