package sn.rts.caisse.model;

/**
 * Mode de gestion du timbre fiscal pour une caisse.
 *
 * <ul>
 *   <li>{@link #AUTO} : le timbre est calculé automatiquement par le backend
 *       selon les règles de la caisse (seuil, taux, modes, catégories).</li>
 *   <li>{@link #MANUEL} : le calcul automatique est désactivé pour la caisse ;
 *       le caissier saisit le timbre à chaque opération (optionnel, peut être
 *       vide = aucun timbre).</li>
 * </ul>
 */
public enum ModeTimbre {
    AUTO,
    MANUEL
}
