package sn.rts.caisse.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcul automatique du timbre fiscal applique aux operations de caisse RTS.
 *
 * <h2>Regle metier</h2>
 * <ul>
 *   <li>Si {@code montant >= 20 000 FCFA} (seuil inclusif) -> timbre = 1%
 *       du montant, arrondi au FCFA (pas de centimes).</li>
 *   <li>Si {@code montant < 20 000 FCFA} -> timbre = 0 (pas d'application).</li>
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
     * Calcule le timbre fiscal a appliquer pour un montant HT donne.
     *
     * @param montantHt montant hors taxe en FCFA (jamais null en pratique car
     *                  valide par les contraintes DTO en amont)
     * @return timbre arrondi au FCFA (HALF_UP), ou 0 si en dessous du seuil
     */
    public BigDecimal calculer(BigDecimal montantHt) {
        if (montantHt == null) return BigDecimal.ZERO;
        if (montantHt.compareTo(SEUIL_APPLICATION) < 0) {
            return BigDecimal.ZERO;
        }
        return montantHt.multiply(TAUX).setScale(0, RoundingMode.HALF_UP);
    }
}
