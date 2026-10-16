package dev.campuspilot.api.projection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the attendance dashboard, straight out of the
 * v_student_dashboard view.
 *
 * <p>Every number here was computed by attendance_health() inside
 * PostgreSQL. This interface only carries the values across the wire —
 * no arithmetic happens in Java. That separation is the whole design.
 *
 * <p>COUNT() returns bigint in Postgres, so the count columns are Long
 * here rather than Integer. Using Integer here is one of those type
 * mismatches that only fails at runtime, under real data, once the
 * number gets large.
 */
public interface DashboardRow {

    UUID getSubjectId();

    String getSubjectName();

    String getSubjectCode();

    Integer getSemester();

    BigDecimal getTargetAttendance();

    Integer getRemainingClassesEstimate();

    /** Classes held. Long, because COUNT() is bigint in PostgreSQL. */
    Long getTotalHeld();

    Long getAttended();

    Long getMissed();

    /** Current attendance percentage. Null when no classes are recorded. */
    BigDecimal getPct();

    Integer getCanSkip();

    Integer getMustAttend();

    Boolean getRecoverable();

    String getStatus();

    LocalDate getLastClass();
}