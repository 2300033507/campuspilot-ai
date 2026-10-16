package dev.campuspilot.api.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A subject a student is enrolled in.
 *
 * <p>targetAttendance and remainingClassesEstimate are assumptions, not
 * facts, and the API lets the student edit both. Storing them explicitly
 * is what makes the "recoverable" flag meaningful: without
 * remainingClassesEstimate the calculation degrades to "am I already at
 * target?", which tells a struggling student nothing.
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

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

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