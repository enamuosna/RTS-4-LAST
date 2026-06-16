package sn.rts.caisse.model;

/**
 * Cycle de validation d'une ventilation hebdomadaire des recettes.
 *
 * <p>Reproduit la double signature de la fiche papier RTS :</p>
 * <ul>
 *   <li>{@link #BROUILLON} : générée, non encore contrôlée.</li>
 *   <li>{@link #CONTROLE_1} : Contrôle 1 signé par le Chef Unité Finances.</li>
 *   <li>{@link #VALIDEE} : Contrôle 2 signé par le Chef de Département (final).</li>
 * </ul>
 */
public enum StatutRecette {
    BROUILLON,
    CONTROLE_1,
    VALIDEE
}
