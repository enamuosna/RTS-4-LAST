package sn.rts.caisse.model;

/**
 * Type(s) d'opération qu'une caisse est autorisée à effectuer.
 * Défini par l'ADMIN à la création de la caisse.
 *
 * <p>Le guichet adapte son affichage en conséquence : si un seul type
 * est autorisé, le caissier ne voit (et ne peut saisir) que celui-ci ;
 * si {@link #TOUS}, il choisit librement encaissement ou décaissement.</p>
 */
public enum TypeOperationAutorise {

    /** Caisse d'encaissement uniquement (opérations {@link TypeOperation#ENTREE}). */
    ENTREE,

    /** Caisse de décaissement uniquement (opérations {@link TypeOperation#SORTIE}). */
    SORTIE,

    /** Caisse mixte : encaissements ET décaissements autorisés. */
    TOUS
}
