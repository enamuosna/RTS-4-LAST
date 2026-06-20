package sn.rts.caisse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import sn.rts.caisse.model.Langue;

/** DTO de la langue de diffusion (référentiel). */
public record LangueDto(
        Long id,
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 60) String libelle,
        boolean actif
) {
    public static LangueDto from(Langue l) {
        return new LangueDto(l.getId(), l.getCode(), l.getLibelle(), l.isActif());
    }
}
