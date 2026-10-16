package dev.campuspilot.api.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Every error response from this API has this shape.
 *
 * <p>One shape for all failures means the frontend writes one error
 * handler instead of guessing at whatever the server happened to return.
 * Consistency in error responses is a quiet feature that users only
 * notice when it is missing.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<Map<String, String>> fieldErrors
) {
}