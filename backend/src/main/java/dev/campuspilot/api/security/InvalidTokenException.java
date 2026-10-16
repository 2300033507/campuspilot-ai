package dev.campuspilot.api.security;

/**
 * Thrown when a token is absent, malformed, expired, or fails signature
 * verification.
 *
 * <p>Deliberately carries no detail about which of those it was. The
 * message is fixed, so an attacker probing the endpoint cannot use the
 * error text to learn which tokens are merely expired versus forged.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public static InvalidTokenException missing() {
        return new InvalidTokenException("Authentication required");
    }
}