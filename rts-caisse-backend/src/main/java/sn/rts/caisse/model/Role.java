package sn.rts.caisse.model;

/**
 * Rôles applicatifs. Chaque rôle détermine les endpoints accessibles
 * via Spring Security (voir SecurityConfig).
 */
public enum Role {

    /** Administration globale : utilisateurs, caisses, paramétrage. */
    ADMIN,

    /** Supervision : dashboards, clôtures, validation des écarts. */
    SUPERVISEUR,

    /** Agent de guichet : saisie des encaissements / décaissements. */
    CAISSIER,

    /**
     * Agent de recette : superviseur de proximité, rattaché à une caisse
     * spécifique. Peut <b>modifier</b> et <b>réactiver</b> les opérations
     * de SA caisse en cas d'erreur de saisie ou d'annulation accidentelle
     * du caissier. Le caissier lui-même ne dispose pas de ces actions.
     */
    AGENT_RECETTE,

    /**
     * Contrôleur : acteur en <b>lecture seule</b>. Il consulte les
     * transactions (opérations de caisse, versements bancaires, journaux,
     * vue de supervision) à des fins de contrôle, mais ne peut
     * <b>jamais</b> créer, modifier, annuler ni supprimer quoi que ce soit.
     * Aucun endpoint de mutation ne l'autorise (cf. {@code @PreAuthorize}
     * des controllers).
     */
    CONTROLEUR,

    /**
     * Chef de l'Unité Finances : signataire du <b>Contrôle 1</b> de la
     * ventilation hebdomadaire des recettes (premier niveau de validation).
     * Accède en lecture à la rubrique « Recettes » de l'application web.
     */
    CHEF_UNITE_FINANCES,

    /**
     * Chef de Département : signataire du <b>Contrôle 2</b> de la ventilation
     * hebdomadaire des recettes (validation finale, après le Contrôle 1).
     * Accède en lecture à la rubrique « Recettes » de l'application web.
     */
    CHEF_DEPARTEMENT
}
