package sn.rts.caisse.guichet.api;

/**
 * Exception levée lors d'un appel API échoué (statut HTTP != 2xx,
 * réseau injoignable, désérialisation invalide).
 */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(String message) {
        super(message);
        this.status = -1;
    }

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() { return status; }
}
