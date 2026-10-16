package dev.campuspilot.api.security;

import java.util.UUID;

/**
 * The authenticated caller, as far as the rest of the application is
 * concerned.
 *
 * <p>Holds only a profile id. Everything else — full name, email, role —
 * is deliberately absent, so no controller can read a claim that might
 * have changed since the token was signed.
 */
public record AuthenticatedProfile(UUID profileId) {
}