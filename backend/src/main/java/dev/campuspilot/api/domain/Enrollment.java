package dev.campuspilot.api.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Which students sit in a course offering.
 *
 * <p>This table is the many-to-many that the original model was missing.
 * It is what turns "my subjects" from a private list into a real class
 * roster a teacher can mark against.
 */
@Entity
@Table(name = "enrollments")
@Getter
@Setter
@NoArgsConstructor
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "enrolled_at", nullable = false, insertable = false, updatable = false)
    private Instant enrolledAt;
}