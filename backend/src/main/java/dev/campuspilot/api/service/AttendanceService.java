package dev.campuspilot.api.service;

import dev.campuspilot.api.domain.AttendanceRecord;
import dev.campuspilot.api.domain.Subject;
import dev.campuspilot.api.exception.ConflictException;
import dev.campuspilot.api.exception.ForbiddenException;
import dev.campuspilot.api.exception.NotFoundException;
import dev.campuspilot.api.projection.RosterEntry;
import dev.campuspilot.api.repository.AttendanceRecordRepository;
import dev.campuspilot.api.repository.EnrollmentRepository;
import dev.campuspilot.api.repository.RosterQueryRepository;
import dev.campuspilot.api.security.AuthenticatedProfile;
import dev.campuspilot.api.web.dto.AttendanceResponse;
import dev.campuspilot.api.web.dto.MarkAttendanceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Recording attendance.
 *
 * <p>THE RULE THAT MATTERS: every method that WRITES requires
 * canMarkAttendance(). Students can read their own records and nothing
 * else. The role check lives here, in the service, before any repository
 * is touched — not in the controller, and certainly not only in the UI.
 */
@Service
public class AttendanceService {

    private final AttendanceRecordRepository records;
    private final EnrollmentRepository enrollments;
    private final SubjectService subjectService;
    private final RosterQueryRepository rosterRepository;

    public AttendanceService(AttendanceRecordRepository records,
                             EnrollmentRepository enrollments,
                             SubjectService subjectService,
                             RosterQueryRepository rosterRepository) {
        this.records = records;
        this.enrollments = enrollments;
        this.subjectService = subjectService;
        this.rosterRepository = rosterRepository;
    }

    /**
     * Read one student's own records.
     *
     * <p>Scoped by the CALLER's id, never by a parameter. There is no
     * "show me student X's attendance" path here at all, so there is no
     * way for one student to ask for another's.
     */
    @Transactional(readOnly = true)
    public List<AttendanceResponse> listOwn(AuthenticatedProfile caller, UUID subjectId) {
        subjectService.requireVisible(caller, subjectId);
        return records.findBySubjectIdAndProfileIdOrderByHeldOnDesc(subjectId, caller.profileId())
                .stream()
                .map(AttendanceResponse::from)
                .toList();
    }

    /**
     * Faculty view: who is enrolled, and where each one stands.
     *
     * <p>Comes from subject_roster() in PostgreSQL, so the health figures
     * are the same ones the student sees — computed by the same function,
     * not re-derived here.
     */
    @Transactional(readOnly = true)
    public List<RosterEntry> roster(AuthenticatedProfile caller, UUID subjectId) {
        if (!caller.canMarkAttendance()) {
            throw ForbiddenException.facultyOnly();
        }
        subjectService.requireOwnedByCaller(caller, subjectId);
        return rosterRepository.rosterFor(subjectId);
    }

    /**
     * Faculty view: what has already been recorded for one date.
     * Empty result means the roll call has not been taken yet.
     */
    @Transactional(readOnly = true)
    public List<AttendanceResponse> rollCall(AuthenticatedProfile caller, UUID subjectId,
                                             LocalDate heldOn) {
        if (!caller.canMarkAttendance()) {
            throw ForbiddenException.facultyOnly();
        }
        subjectService.requireOwnedByCaller(caller, subjectId);
        return records.findRollCall(subjectId, heldOn).stream()
                .map(AttendanceResponse::from)
                .toList();
    }

    /**
     * Write. Faculty and admin only, for a subject they own, for a
     * student who is actually enrolled.
     */
    @Transactional
    public AttendanceResponse mark(AuthenticatedProfile caller, UUID subjectId,
                                   UUID studentId, MarkAttendanceRequest request) {

        if (!caller.canMarkAttendance()) {
            throw ForbiddenException.studentsCannotMarkAttendance();
        }

        // Throws unless this caller teaches the subject (or is admin).
        Subject subject = subjectService.requireOwnedByCaller(caller, subjectId);

        if (!enrollments.isEnrolled(subjectId, studentId)) {
            // Recording against a non-student creates rows nothing reads,
            // and quietly inflates the class average. Refuse.
            throw new NotFoundException("That student is not enrolled in this subject");
        }

        if (records.existsBySubjectIdAndProfileIdAndHeldOn(subjectId, studentId, request.heldOn())) {
            throw new ConflictException("Attendance for " + request.heldOn()
                    + " is already recorded for this student in this subject");
        }

        AttendanceRecord r = new AttendanceRecord();
        r.setSubjectId(subject.getId());
        r.setProfileId(studentId);
        r.setHeldOn(request.heldOn());
        r.setStatus(request.status());
        r.setNote(request.note());
        r.setMarkedBy(caller.profileId());   // who recorded it

        return AttendanceResponse.from(records.save(r));
    }

    /** Correction. Same gate as marking, plus the record must match. */
    @Transactional
    public void delete(AuthenticatedProfile caller, UUID subjectId, UUID recordId) {
        if (!caller.canMarkAttendance()) {
            throw ForbiddenException.studentsCannotMarkAttendance();
        }

        subjectService.requireOwnedByCaller(caller, subjectId);

        AttendanceRecord r = records.findById(recordId)
                .orElseThrow(() -> new NotFoundException("No attendance record with id " + recordId));

        if (!r.getSubjectId().equals(subjectId)) {
            // Record ids are caller-supplied, so belonging to THIS subject
            // must be checked, not just existence.
            throw new ConflictException("That record does not belong to this subject");
        }

        records.delete(r);
    }

    /** Today's date, used by the faculty roll-call UI. */
    public LocalDate today() {
        return LocalDate.now();
    }
}