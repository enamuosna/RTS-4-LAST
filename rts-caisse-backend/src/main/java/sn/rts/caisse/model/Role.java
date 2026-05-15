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
    AGENT_RECETTE
}
