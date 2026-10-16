package dev.campuspilot.api.projection;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line of subject_roster() — a faculty member's view of a class.
 *
 * <p>Every figure comes from attendance_health() inside PostgreSQL, so
 * the percentage a teacher sees is produced by exactly the same code
 * path as the one the student sees. Re-deriving it here would create two
 * implementations that can drift apart.
 */
public interface RosterEntry {

    UUID getProfileId();

    String getFullName();

    String getEmail();

    BigDecimal getPct();

    Integer getCanSkip();

    Integer getMustAttend();

    Boolean getRecoverable();

    String getStatus();
}