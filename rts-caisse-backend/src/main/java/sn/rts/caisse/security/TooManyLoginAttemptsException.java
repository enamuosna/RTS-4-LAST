package sn.rts.caisse.security;

import lombok.Getter;

/**
 * Levee par le controller de login quand l'IP cliente a depasse le seuil
 * de rate limiting. Mappee en HTTP 429 par le GlobalExceptionHandler avec
 * un header {@code Retry-After} indiquant le nombre de secondes a attendre.
 */
@Getter
public class TooManyLoginAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super("Trop de tentatives de connexion. Reessayez dans "
                + retryAfterSeconds + " secondes.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
