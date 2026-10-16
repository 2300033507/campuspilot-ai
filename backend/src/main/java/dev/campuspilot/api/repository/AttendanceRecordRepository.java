package dev.campuspilot.api.repository;

import dev.campuspilot.api.domain.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    List<AttendanceRecord> findBySubjectIdOrderByHeldOnDesc(UUID subjectId);

    Optional<AttendanceRecord> findBySubjectIdAndHeldOn(UUID subjectId, LocalDate heldOn);

    /**
     * Guards the unique constraint before the database has to. A friendly
     * 409 beats a raw ConstraintViolationException trace.
     */
    boolean existsBySubjectIdAndHeldOn(UUID subjectId, LocalDate heldOn);
}