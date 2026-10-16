package dev.campuspilot.api.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One student's attendance for one class.
 *
 * <p>Every row counts toward the total whether the student was present
 * or absent. That is what makes attendance fall.
 *
 * <p>Two things changed in this model, and both matter:
 *
 * <ul>
 *   <li><b>profileId</b> — a record belongs to a STUDENT, not to a
 *       subject. It was previously implied by subject ownership, which
 *       meant a teacher had no way to mark a class for a room.</li>
 *   <li><b>markedBy / markedAt</b> — who recorded it and when. A
 *       disputed attendance mark should be traceable rather than
 *       arguable, and a late correction should be visible as one.</li>
 * </ul>
 *
 * <p>A student never creates these rows. Only the teacher of the
 * offering (or an admin) does — enforced in AttendanceService, not by
 * hiding the button.
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

    /** Whose attendance this is. */
    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "held_on", nullable = false)
    private LocalDate heldOn;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "note")
    private String note;

    /** The teacher who recorded it. Null on legacy rows migrated from V3. */
    @Column(name = "marked_by")
    private UUID markedBy;

    @Column(name = "marked_at", nullable = false, insertable = false, updatable = false)
    private Instant markedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}