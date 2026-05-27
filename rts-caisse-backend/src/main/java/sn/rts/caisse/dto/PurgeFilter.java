package sn.rts.caisse.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Critères de purge des opérations de caisse.
 *
 * <p>Utilisé par {@code OperationPurgeService} pour les 3 actions
 * « prévisualiser », « exporter CSV » et « purger en masse ».</p>
 *
 * @param caisseId   filtre optionnel sur une caisse précise (null = toutes)
 * @param avantDate  borne supérieure obligatoire : on supprime les opérations
 *                   dont {@code dateOperation < avantDate 00:00:00}
 * @param statut     statut des opérations cibles
 */
public record PurgeFilter(
        Long caisseId,
        @NotNull LocalDate avantDate,
        @NotNull Statut statut) {

    /** Statut des opérations à purger. */
    public enum Statut {
        /** Toutes les opérations (annulées + actives). ⚠ contre-pass automatique des actives. */
        TOUS,
        /** Seules les opérations déjà annulées (le plus sûr : solde déjà ajusté). */
        SEULEMENT_ANNULEES,
        /** Seules les opérations actives (jamais annulées). Contre-pass auto. */
        SEULEMENT_ACTIVES
    }
}
