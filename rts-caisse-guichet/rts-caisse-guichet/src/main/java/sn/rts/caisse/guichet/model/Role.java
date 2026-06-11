package sn.rts.caisse.guichet.model;

// CONTROLEUR : acteur web en lecture seule. Listé ici uniquement pour que
// la désérialisation JSON de l'AuthResponse ne casse pas si un tel compte
// tente de se connecter au guichet ; le LoginController lui refuse ensuite
// proprement l'accès (guichet réservé caissier / agent de recette / superviseur).
public enum Role { ADMIN, SUPERVISEUR, CAISSIER, AGENT_RECETTE, CONTROLEUR }
