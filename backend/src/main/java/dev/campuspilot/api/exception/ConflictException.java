package dev.campuspilot.api.exception;

/** The request is valid in shape but conflicts with existing data. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}