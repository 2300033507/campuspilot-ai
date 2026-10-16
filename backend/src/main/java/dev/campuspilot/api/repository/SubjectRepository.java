package dev.campuspilot.api.repository;

import dev.campuspilot.api.domain.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    // --- Student scope: subjects this student is enrolled in ---------

    @Query("""
            SELECT s FROM Subject s
            JOIN Enrollment e ON e.subjectId = s.id
            WHERE e.profileId = :profileId
            ORDER BY s.name
            """)
    List<Subject> findEnrolledBy(@Param("profileId") UUID profileId);

    @Query("""
            SELECT s FROM Subject s
            JOIN Enrollment e ON e.subjectId = s.id
            WHERE s.id = :subjectId AND e.profileId = :profileId
            """)
    Optional<Subject> findEnrolledById(@Param("subjectId") UUID subjectId,
                                       @Param("profileId") UUID profileId);

    // --- Faculty scope: subjects this person teaches ------------------

    List<Subject> findByFacultyIdOrderByNameAsc(UUID facultyId);

    Optional<Subject> findByIdAndFacultyId(UUID id, UUID facultyId);

    // --- Admin scope: everything --------------------------------------

    List<Subject> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCaseAndCode(String name, String code);
}