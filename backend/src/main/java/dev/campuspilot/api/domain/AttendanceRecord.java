package dev.campuspilot.api.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per class that was actually held.
 *
 * <p>Every row counts toward the total whether the student was present or
 * absent. That is what makes attendance fall. A table that only stored
 * "present" records could never show a shortfall.
 *
 * <p>The unique constraint on (subject_id, held_on) is enforced in the
 * database, not here, so a double-submitted form fails at the last
 * possible moment rather than being caught by fragile Java code.
 */
@Entity
@Table(name = "attendance_records")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "held_on", nullable = false)
    private LocalDate heldOn;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}