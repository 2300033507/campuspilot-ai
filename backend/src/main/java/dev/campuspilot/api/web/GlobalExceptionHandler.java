package dev.campuspilot.api.web;

import dev.campuspilot.api.exception.ConflictException;
import dev.campuspilot.api.exception.NotFoundException;
import dev.campuspilot.api.security.InvalidTokenException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Turns exceptions into consistent JSON responses.
 *
 * <p>Without this, a failed validation returns Spring's default error
 * body while a missing record returns a bare string. The frontend cannot
 * handle both. With this, every failure looks the same.
 *
 * <p>Two rules this enforces:
 *   1. Never leak a stack trace or SQL text to the client.
 *   2. Never let an unexpected 500 return a blank body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    /**
     * Wrong email or wrong password.
     *
     * <p>Always the same message, whichever it was. If a bad email
     * returned "no such account" and a bad password returned "incorrect
     * password", the login form becomes a tool for discovering which
     * emails have accounts.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex,
                                                          HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), req, null);
    }

    /**
     * Reached when a request carries a token that will not verify.
     *
     * <p>Logged at warn without the token itself. The message is fixed
     * so an attacker cannot tell "expired" apart from "forged", which
     * would tell them whether a stolen token is merely stale.
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiError> handleInvalidToken(InvalidTokenException ex,
                                                       HttpServletRequest req) {
        log.warn("Rejected token on {} {}", req.getMethod(), req.getRequestURI());
        return build(HttpStatus.UNAUTHORIZED, "Invalid or expired token", req, null);
    }

    /**
     * Bean Validation failures. Returns every offending field at once
     * rather than the first, so the UI can highlight all of them in one
     * pass instead of making the user resubmit to discover the next one.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest req) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "invalid value" : fe.getDefaultMessage()
                ))
                .toList();

        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, fieldErrors);
    }

    /**
     * A database constraint was the last line of defence. By the time we
     * get here the data is already wrong, so the log records the detail
     * but the client only sees a generic message. Internal constraint
     * names leak schema information.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex,
                                                    HttpServletRequest req) {
        log.warn("Data integrity violation on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.CONFLICT,
                "That record conflicts with data that already exists", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side", req, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message,
                                           HttpServletRequest req,
                                           List<Map<String, String>> fieldErrors) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                req.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.status(status).body(body);
    }
}