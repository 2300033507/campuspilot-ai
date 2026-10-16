package dev.campuspilot.api.security;

import java.util.UUID;

/**
 * The authenticated caller: who they are and what they may do.
 *
 * <p>Holds nothing else. No email, no name, no password hash. A token
 * can be up to 24 hours old, so any claim beyond identity could be
 * stale — a promoted teacher demoted an hour ago would still read as
 * faculty from a token minted before the change.
 */
public record AuthenticatedProfile(UUID profileId, String role) {

    public boolean isStudent() {
        return "student".equals(role);
    }

    public boolean isFaculty() {
        return "faculty".equals(role);
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }

    /**
     * True for anyone who may record or correct attendance.
     *
     * <p>Note this is deliberately NOT "not a student". Treating the
     * absence of a check as permission is how "student" accidentally
     * ends up able to mark themselves present. Access is granted by
     * naming the roles that have it.
     */
    public boolean canMarkAttendance() {
        return isFaculty() || isAdmin();
    }
}