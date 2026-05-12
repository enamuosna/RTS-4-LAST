package sn.rts.caisse.guichet.model;

public enum ModePaiement {
    ESPECES("Espèces"),
    CHEQUE("Chèque"),
    VIREMENT("Virement"),
    CARTE_BANCAIRE("Carte bancaire"),
    WAVE("Wave"),
    ORANGE_MONEY("Orange Money"),
    FREE_MONEY("Free Money");

    private final String libelle;
    ModePaiement(String libelle) { this.libelle = libelle; }
    public String getLibelle() { return libelle; }

    @Override public String toString() { return libelle; }
}
