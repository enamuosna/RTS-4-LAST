package sn.rts.caisse.dto;

import jakarta.validation.constraints.NotNull;
import sn.rts.caisse.model.OperationDiffusion;

import java.time.LocalDateTime;

/**
 * Un créneau de diffusion (date+heure + langue optionnelle).
 * Utilisé en entrée (création/modif d'opération) et en sortie (réponse).
 * En entrée, {@code langueLibelle} est ignoré (résolu côté serveur).
 */
public record DiffusionDto(
        @NotNull LocalDateTime dateHeure,
        Long langueId,
        String langueLibelle
) {
    public static DiffusionDto from(OperationDiffusion d) {
        return new DiffusionDto(d.getDateHeure(), d.getLangueId(), d.getLangueLibelle());
    }
}
