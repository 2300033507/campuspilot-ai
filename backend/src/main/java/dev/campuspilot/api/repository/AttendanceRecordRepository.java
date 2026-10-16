package dev.campuspilot.api.repository;

import dev.campuspilot.api.domain.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    /** A student's own records for one subject. */
    List<AttendanceRecord> findBySubjectIdAndProfileIdOrderByHeldOnDesc(
            UUID subjectId, UUID profileId);

    /** Everyone's records for a subject on one day — a roll call. */
    @Query("""
            SELECT ar FROM AttendanceRecord ar
            WHERE ar.subjectId = :subjectId AND ar.heldOn = :heldOn
            """)
    List<AttendanceRecord> findRollCall(@Param("subjectId") UUID subjectId,
                                        @Param("heldOn") LocalDate heldOn);

    Optional<AttendanceRecord> findBySubjectIdAndProfileIdAndHeldOn(
            UUID subjectId, UUID profileId, LocalDate heldOn);

    boolean existsBySubjectIdAndProfileIdAndHeldOn(UUID subjectId, UUID profileId, LocalDate heldOn);

    long countBySubjectId(UUID subjectId);
}