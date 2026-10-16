package dev.campuspilot.api.repository;

import dev.campuspilot.api.domain.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    List<Enrollment> findByProfileId(UUID profileId);

    List<Enrollment> findBySubjectId(UUID subjectId);

    Optional<Enrollment> findByProfileIdAndSubjectId(UUID profileId, UUID subjectId);

    boolean existsByProfileIdAndSubjectId(UUID profileId, UUID subjectId);

    long countBySubjectId(UUID subjectId);

    /**
     * Confirms a student actually sits in this offering before attendance
     * is recorded against them. Marking attendance for someone who is not
     * enrolled would silently create records nothing can see.
     */
    @Query("""
            SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
            FROM Enrollment e
            WHERE e.subjectId = :subjectId AND e.profileId = :profileId
            """)
    boolean isEnrolled(@Param("subjectId") UUID subjectId,
                       @Param("profileId") UUID profileId);
}