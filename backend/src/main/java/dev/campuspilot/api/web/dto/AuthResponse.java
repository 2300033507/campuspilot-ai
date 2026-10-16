package dev.campuspilot.api.web.dto;

import java.util.UUID;

/**
 * What a successful register or login returns.
 *
 * <p>Note what is absent: the password hash. Never in a DTO, never in a
 * log line, never in an error message. The password goes in, a token
 * comes out, and the server keeps the hash to itself.
 *
 * @param profileId       the caller's own id, so the client can display
 *                        their data without a second request
 * @param fullName        display only
 * @param role            student or faculty
 * @param token           the JWT to send as "Authorization: Bearer ..."
 * @param expiresInSeconds lifetime, so the client knows when to re-login
 *                        instead of guessing and getting a surprise 401
 */
public record AuthResponse(
        UUID profileId,
        String fullName,
        String role,
        String token,
        long expiresInSeconds
) {
}