package sn.rts.caisse.guichet.util;

import sn.rts.caisse.guichet.model.Dto.AuthResponse;
import sn.rts.caisse.guichet.model.Dto.CaisseDTO;

/**
 * Singleton en mémoire conservant l'état de la session : utilisateur connecté,
 * JWT et caisse actuellement sélectionnée par le caissier.
 */
public final class Session {

    private static final Session INSTANCE = new Session();

    public static Session getInstance() { return INSTANCE; }

    private Session() {}

    private AuthResponse auth;
    private CaisseDTO caisseActive;

    public void setAuth(AuthResponse auth) {
        this.auth = auth;
    }

    public AuthResponse getAuth() {
        return auth;
    }

    public String getToken() {
        return auth != null ? auth.token : null;
    }

    public boolean isAuthenticated() {
        return auth != null && auth.token != null;
    }

    public CaisseDTO getCaisseActive() { return caisseActive; }

    public void setCaisseActive(CaisseDTO caisse) { this.caisseActive = caisse; }

    public void clear() {
        this.auth = null;
        this.caisseActive = null;
    }
}
