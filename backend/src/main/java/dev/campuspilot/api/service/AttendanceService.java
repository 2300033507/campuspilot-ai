package dev.campuspilot.api.service;

import dev.campuspilot.api.domain.AttendanceRecord;
import dev.campuspilot.api.domain.Subject;
import dev.campuspilot.api.exception.ConflictException;
import dev.campuspilot.api.repository.AttendanceRecordRepository;
import dev.campuspilot.api.repository.SubjectRepository;
import dev.campuspilot.api.web.dto.AttendanceResponse;
import dev.campuspilot.api.web.dto.MarkAttendanceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AttendanceService {

    private final AttendanceRecordRepository records;
    private final SubjectRepository subjects;

    public AttendanceService(AttendanceRecordRepository records, SubjectRepository subjects) {
        this.records = records;
        this.subjects = subjects;
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> listFor(UUID profileId, UUID subjectId) {
        requireOwnedSubject(profileId, subjectId);
        return records.findBySubjectIdOrderByHeldOnDesc(subjectId).stream()
                .map(AttendanceResponse::from)
                .toList();
    }

    @Transactional
    public AttendanceResponse mark(UUID profileId, UUID subjectId, MarkAttendanceRequest request) {
        requireOwnedSubject(profileId, subjectId);

        // Marking the same day twice would corrupt the counts silently.
        // The unique constraint catches it regardless; this turns a driver
        // error into a message a student can act on.
        if (records.existsBySubjectIdAndHeldOn(subjectId, request.heldOn())) {
            throw new ConflictException(
                    "Attendance for " + request.heldOn() + " is already recorded for this subject");
        }

        AttendanceRecord r = new AttendanceRecord();
        r.setSubjectId(subjectId);
        r.setHeldOn(request.heldOn());
        r.setStatus(request.status());
        r.setNote(request.note());

        return AttendanceResponse.from(records.save(r));
    }

    @Transactional
    public void delete(UUID profileId, UUID subjectId, UUID recordId) {
        requireOwnedSubject(profileId, subjectId);

        AttendanceRecord r = records.findById(recordId).orElseThrow(() ->
                new ConflictException("No attendance record with id " + recordId));

        // Second ownership check: the record id is caller-supplied, so
        // matching it to this subject matters. Without it, any record id
        // could be deleted from any owned subject.
        if (!r.getSubjectId().equals(subjectId)) {
            throw new ConflictException("That record does not belong to this subject");
        }

        records.delete(r);
    }

    private Subject requireOwnedSubject(UUID profileId, UUID subjectId) {
        return subjects.findByIdAndProfileId(subjectId, profileId)
                .orElseThrow(() -> dev.campuspilot.api.exception.NotFoundException.subject(subjectId));
    }
}