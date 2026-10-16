package dev.campuspilot.api.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A COURSE OFFERING — one subject as taught by one teacher.
 *
 * <p>Deliberately not "a student's subject". The earlier model gave every
 * student a private copy of every subject, which meant two students in
 * the same class shared nothing and a teacher marking attendance for one
 * would know nothing about the other. Students now reach a subject
 * through {@link Enrollment}.
 *
 * <p>targetAttendance and remainingClassesEstimate are assumptions, not
 * facts, and are editable by whoever owns the offering. Storing them
 * explicitly is what makes the "recoverable" flag meaningful.
 */
@Entity
@Table(name = "subjects")
@Getter
@Setter
@NoArgsConstructor
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The teacher. Nullable means the offering is not yet assigned to
     * anyone, which is a legitimate state for a freshly created course —
     * and one where only an admin may add attendance to it.
     */
    @Column(name = "faculty_id")
    private UUID facultyId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "code")
    private String code;

    @Column(name = "semester", nullable = false)
    private Integer semester;

    @Column(name = "target_attendance", nullable = false)
    private BigDecimal targetAttendance = new BigDecimal("75.00");

    @Column(name = "remaining_classes_estimate", nullable = false)
    private Integer remainingClassesEstimate = 20;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}