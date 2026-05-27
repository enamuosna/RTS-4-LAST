package sn.rts.caisse.security;

import lombok.Getter;

/**
 * Levee quand un admin essaie de faire une operation backup (export/import)
 * trop rapprochee de la precedente. Mappee en HTTP 429 Too Many Requests
 * avec un header Retry-After.
 */
@Getter
public class TooManyBackupRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyBackupRequestsException(long retryAfterSeconds) {
        super("Operation backup trop frequente. Reessayez dans "
                + retryAfterSeconds + " secondes (une operation par heure max).");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
