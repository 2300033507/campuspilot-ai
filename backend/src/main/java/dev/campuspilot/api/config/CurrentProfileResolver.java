package dev.campuspilot.api.config;

import dev.campuspilot.api.security.AuthenticatedProfile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the authenticated caller.
 *
 * <p>THIS CLASS IS THE ENTIRE REASON THE AUTH SWAP WAS CHEAP.
 *
 * <p>Before authentication existed, this read an X-Profile-Id header —
 * which anyone could set to anything, so the app was openly insecure. It
 * now reads the profile id out of the verified JWT instead.
 *
 * <p>Nothing else in the codebase changed. That is because every service
 * already accepted a profileId as a parameter and never resolved identity
 * itself: the controller decides who the caller is, then passes the
 * answer down. Controllers are the only place that needs to know how
 * authentication works, so swapping the mechanism touched one file.
 *
 * <p>If this class is ever bypassed, or a profile id is taken from
 * anywhere else, the app is insecure again.
 */
@Component
public class CurrentProfileResolver {

    /**
     * @throws AccessDeniedException if called without a token. Spring
     *         Security should already have rejected such a request;
     *         reaching this point means a security rule is
     *         misconfigured, so failing loudly is correct.
     */
    public UUID resolve() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedProfile profile)) {
            throw new AccessDeniedException("No authenticated principal in the security context");
        }

        return profile.profileId();
    }
}