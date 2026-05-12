package sn.rts.caisse.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import sn.rts.caisse.model.ModePaiement;
import sn.rts.caisse.model.TypeOperation;

import java.math.BigDecimal;

/**
 * Payload de création d'une opération de caisse.
 *
 * <p>Évolutions :</p>
 * <ul>
 *   <li><b>v2 (mai 2026)</b> : ajout du champ {@code banqueId}. Il reste
 *       optionnel au niveau du record, mais devient obligatoire dans
 *       {@link sn.rts.caisse.service.OperationCaisseService#enregistrer}
 *       lorsque le mode de paiement est {@code CHEQUE} ou {@code VIREMENT}.</li>
 *   <li><b>v2</b> : le motif n'est plus contraint par {@code @NotBlank}
 *       (il a été retiré du formulaire côté guichet) ; il reste limité
 *       à 500 caractères pour les import historiques.</li>
 * </ul>
 */
public record OperationCaisseRequest(

        @NotNull
        Long caisseId,

        @NotNull
        Long categorieId,

        Long clientId,

        @NotNull
        TypeOperation typeOperation,

        @NotNull
        @Positive
        BigDecimal montant,

        @NotNull
        ModePaiement modePaiement,

        @Size(max = 500)
        String motif,

        @Size(max = 100)
        String reference,

        /**
         * Banque émettrice. Obligatoire en pratique pour les paiements par
         * chèque ou virement (validation côté service). {@code null} pour
         * espèces, Wave, Orange Money, Free Money, carte bancaire.
         */
        Long banqueId
) {
}