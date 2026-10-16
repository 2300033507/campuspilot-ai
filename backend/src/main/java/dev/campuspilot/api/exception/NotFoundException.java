package dev.campuspilot.api.exception;

/** The requested resource does not exist, or is not the caller's to see. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException subject(Object id) {
        return new NotFoundException("No subject found with id " + id);
    }

    public static NotFoundException profile(Object id) {
        return new NotFoundException("No profile found with id " + id);
    }
}