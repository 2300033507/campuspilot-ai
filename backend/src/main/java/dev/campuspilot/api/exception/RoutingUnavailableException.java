package dev.campuspilot.api.exception;

/**
 * The upstream routing service failed or answered unusably.
 *
 * <p>Distinct from a 500 because the fault is upstream, not ours. The
 * caller should retry later, not report a broken request — so 502, and
 * a message that says which part failed.
 */
public class RoutingUnavailableException extends RuntimeException {

    public RoutingUnavailableException(String message) {
        super(message);
    }

    public RoutingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}