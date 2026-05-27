package sn.rts.caisse.security;

import lombok.Getter;

/**
 * Levee quand un utilisateur tente de se connecter sur un compte
 * verrouille apres trop d'echecs successifs. Mappee en HTTP 423
 * "Locked" par le GlobalExceptionHandler, avec le nombre de secondes
 * restantes avant deverrouillage automatique.
 */
@Getter
public class AccountLockedException extends RuntimeException {

    private final long retryAfterSeconds;

    public AccountLockedException(long retryAfterSeconds) {
        super("Compte verrouille suite a trop d'echecs de connexion. "
                + "Reessayez dans " + retryAfterSeconds + " secondes.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
