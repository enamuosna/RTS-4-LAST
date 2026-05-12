package sn.rts.caisse.model;

public enum ModePaiement {
    ESPECES("Espèces"),
    CHEQUE("Chèque"),
    VIREMENT("Virement bancaire"),
    CARTE_BANCAIRE("Carte bancaire"),
    WAVE("Wave"),
    ORANGE_MONEY("Orange Money"),
    FREE_MONEY("Free Money");

    private final String libelle;

    ModePaiement(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}