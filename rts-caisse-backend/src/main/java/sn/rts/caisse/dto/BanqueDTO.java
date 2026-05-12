package sn.rts.caisse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import sn.rts.caisse.model.Banque;

public record BanqueDTO(
        Long id,
        @NotBlank @Size(max = 10) String code,
        @NotBlank @Size(max = 200) String libelle,
        @Size(max = 80) String pays,
        @Size(max = 20) String codeEtablissement,
        @Size(max = 255) String siteInternet,
        Boolean actif
) {
    public static BanqueDTO from(Banque b) {
        return new BanqueDTO(
                b.getId(),
                b.getCode(),
                b.getLibelle(),
                b.getPays(),
                b.getCodeEtablissement(),
                b.getSiteInternet(),
                b.getActif()
        );
    }
}