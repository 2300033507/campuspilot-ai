package dev.campuspilot.api.config;

import dev.campuspilot.api.exception.ForbiddenException;
import dev.campuspilot.api.security.AuthenticatedProfile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the authenticated caller.
 *
 * <p>THIS CLASS IS THE ENTIRE REASON THE AUTH SWAP WAS CHEAP, and now
 * also the reason the ROLE MODEL could change without touching services.
 *
 * <p>It previously read an X-Profile-Id header, which anyone could set to
 * anything. It then read a profile id out of a verified JWT. It now also
 * carries the role, so controllers can ask "may this person mark
 * attendance" without any service knowing how roles work.
 *
 * <p>Services still never resolve identity themselves. The controller
 * decides who the caller is, then passes the answer down.
 */
@Component
public class CurrentProfileResolver {

    public UUID resolveId() {
        return resolve().profileId();
    }

    public AuthenticatedProfile resolve() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedProfile caller)) {
            throw new AccessDeniedException("No authenticated principal in the security context");
        }
        return caller;
    }

    /**
     * Gate for anything that writes attendance.
     *
     * <p>Every such endpoint calls this before touching a repository. The
     * UI also hides the control, but that is convenience — a hidden
     * button is not a security control, and a student with a token can
     * call any endpoint directly.
     */
    public AuthenticatedProfile requireAttendanceMarker() {
        AuthenticatedProfile caller = resolve();
        if (!caller.canMarkAttendance()) {
            throw ForbiddenException.studentsCannotMarkAttendance();
        }
        return caller;
    }

    public AuthenticatedProfile requireFacultyOrAdmin() {
        AuthenticatedProfile caller = resolve();
        if (!caller.canMarkAttendance()) {
            throw ForbiddenException.facultyOnly();
        }
        return caller;
    }
}