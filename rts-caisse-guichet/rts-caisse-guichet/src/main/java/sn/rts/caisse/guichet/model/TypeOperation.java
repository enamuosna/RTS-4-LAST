package sn.rts.caisse.guichet.model;

public enum TypeOperation {
    ENTREE("Encaissement"),
    SORTIE("Décaissement");

    private final String libelle;
    TypeOperation(String libelle) { this.libelle = libelle; }
    public String getLibelle() { return libelle; }

    @Override public String toString() { return libelle; }
}
