package sn.rts.caisse.service;

import org.springframework.stereotype.Component;
import sn.rts.caisse.model.ModePaiement;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcul automatique du timbre fiscal applique aux operations de caisse RTS.
 *
 * <h2>Regle metier</h2>
 * <ul>
 *   <li>Le timbre s'applique <b>uniquement au paiement en ESPECES</b>.
 *       Cheque, virement, Wave, Orange Money, Free Money et carte bancaire
 *       sont exoneres (le timbre fiscal physique est colle sur le recu
 *       cash, pas sur les paiements electroniques ou bancaires).</li>
 *   <li>Pour les ESPECES : si {@code montant >= 20 000 FCFA} (seuil
 *       inclusif) -> timbre = 1% du montant, arrondi au FCFA.</li>
 *   <li>Sinon (mode != ESPECES, ou montant < 20 000) -> timbre = 0.</li>
 * </ul>
 *
 * <p>Le calcul est <b>autoritatif cote backend</b> : on ignore deliberement
 * toute valeur {@code timbre} envoyee par le client. Cela empeche un
 * client malicieux ou un bug d'IHM de faire passer un mauvais montant.
 * Le front affiche le timbre en temps reel mais reproduit juste la meme
 * formule pour donner un feedback visuel.</p>
 */
@Component
public class TimbreFiscalCalculator {

    /** Seuil d'application du timbre (inclusif), en FCFA. */
    public static final BigDecimal SEUIL_APPLICATION = new BigDecimal("20000");

    /** Taux du timbre en pourcentage du montant (1%). */
    public static final BigDecimal TAUX = new BigDecimal("0.01");

    /**
     * Calcule le timbre fiscal a appliquer pour un montant HT et un mode
     * de paiement donnes.
     *
     * @param montantHt montant hors taxe en FCFA
     * @param mode      mode de paiement (le timbre est exonere sauf ESPECES)
     * @return timbre arrondi au FCFA, ou 0 si non applicable
     */
    public BigDecimal calculer(BigDecimal montantHt, ModePaiement mode) {
        if (montantHt == null) return BigDecimal.ZERO;
        // Exoneration : tout mode autre qu'especes (cheque, virement,
        // mobile money, carte) n'est pas concerne par le timbre fiscal.
        if (mode != ModePaiement.ESPECES) {
            return BigDecimal.ZERO;
        }
        if (montantHt.compareTo(SEUIL_APPLICATION) < 0) {
            return BigDecimal.ZERO;
        }
        return montantHt.multiply(TAUX).setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Variante retrocompatible : montant seul, comportement "comme avant"
     * (sans tenir compte du mode). A utiliser uniquement pour les anciens
     * appelants qui n'ont pas le mode sous la main. <b>Prefere la version
     * avec mode</b> qui applique correctement l'exoneration des modes
     * non-especes.
     *
     * @deprecated Utiliser {@link #calculer(BigDecimal, ModePaiement)}.
     */
    @Deprecated
    public BigDecimal calculer(BigDecimal montantHt) {
        return calculer(montantHt, ModePaiement.ESPECES);
    }
}
