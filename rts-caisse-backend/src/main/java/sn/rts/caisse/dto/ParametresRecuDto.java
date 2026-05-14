package sn.rts.caisse.dto;

import java.util.List;

/**
 * DTO bidirectionnel pour la consultation et la mise à jour des paramètres
 * du reçu PDF. Les couleurs sont au format {@code #RRGGBB} et les tailles
 * en points typographiques.
 *
 * @param sections ordre + visibilité des sections du reçu. L'ordre du tableau
 *                 détermine l'ordre d'affichage ; la propriété {@code visible}
 *                 permet de masquer une section.
 */
public record ParametresRecuDto(
        // En-tête
        String logoTexte,
        String raisonSociale,
        String sousTitreEntete,
        String ligneLegale,
        String capital,
        String adresse,
        String telephone,
        String boitePostale,
        String ninea,

        // Footer
        String footerLigne1,
        String footerLigne2,
        String villeSignature,

        // Couleurs
        String couleurPrimaire,
        String couleurAccent,
        String couleurTexte,
        String couleurTexteSecondaire,
        String couleurSuccess,
        String couleurDanger,
        String couleurFondMontant,

        // Tailles
        Integer tailleTitre,
        Integer tailleEntete,
        Integer tailleCorps,
        Integer tailleMontant,
        Integer tailleFooter,

        // Layout
        List<Section> sections,

        /** True si un logo image a été uploadé. Évite un appel HTTP inutile
         *  côté frontend pour récupérer une image qui n'existe pas. */
        boolean logoPresent
) {
    public record Section(String id, boolean visible) {}
}
