package sn.rts.caisse.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import sn.rts.caisse.model.ModePaiement;
import sn.rts.caisse.model.TypeOperation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

        /** Montant HT saisi par le caissier. */
        @NotNull
        @Positive
        BigDecimal montant,

        /**
         * Timbre fiscal sénégalais (taxe optionnelle). Si null ou non envoyé,
         * traité comme 0 par le service. Le montant TTC est calculé côté
         * serveur (montant + timbre).
         */
        @PositiveOrZero
        BigDecimal timbre,

        /**
         * Mode de saisie du timbre :
         * <ul>
         *   <li>{@code true} → timbre <b>MANUEL</b> : la valeur {@link #timbre}
         *       est utilisée telle quelle ({@code null}/0 = aucun timbre).</li>
         *   <li>{@code false} ou {@code null} → timbre <b>AUTOMATIQUE</b> :
         *       calculé par le backend selon la configuration (comportement
         *       historique par défaut).</li>
         * </ul>
         */
        Boolean timbreManuel,

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
        Long banqueId,

        /**
         * Date+heure de diffusion « principale » (compat / reçu). <b>Optionnel</b>
         * désormais. Si {@link #diffusions} est fourni, la première sert de valeur
         * de référence sur le reçu.
         */
        LocalDateTime dateDiffusion,

        /**
         * Créneaux de diffusion à l'antenne (plusieurs jours/heures possibles),
         * chacun avec une langue optionnelle. Optionnel : liste vide = aucune
         * diffusion. Le « nombre de diffusion » = taille de cette liste.
         */
        java.util.List<DiffusionDto> diffusions,

        /**
         * Nombre de passages à l'antenne (un spot diffusé N fois).
         * <b>Informatif</b> et optionnel : n'influence pas le montant. Saisi au
         * guichet pour les produits dont la catégorie a
         * {@code proposeNombrePassages=true}. {@code null} = non renseigné.
         */
        @Positive
        Integer nombrePassages
) {
}