package sn.rts.caisse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sn.rts.caisse.model.CategorieOperation;
import sn.rts.caisse.model.TypeOperation;

public record CategorieOperationDTO(
        Long id,
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 120) String libelle,
        @NotNull TypeOperation typeOperation,
        boolean actif,
        /**
         * Active la zone d'upload d'un justificatif (PDF/JPG/PNG) lors
         * de la saisie d'une operation. Default false : pas d'upload.
         */
        boolean accepteJustificatif,
        /** Active le choix d'une langue de diffusion (ex. Avis & Communiqués). */
        boolean proposeLangue,
        /** Active la saisie d'un nombre de passages à l'antenne (informatif). */
        boolean proposeNombrePassages
) {
    public static CategorieOperationDTO from(CategorieOperation c) {
        return new CategorieOperationDTO(
                c.getId(),
                c.getCode(),
                c.getLibelle(),
                c.getTypeOperation(),
                c.isActif(),
                c.isAccepteJustificatif(),
                c.isProposeLangue(),
                c.isProposeNombrePassages()
        );
    }
}
