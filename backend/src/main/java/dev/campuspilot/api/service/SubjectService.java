package dev.campuspilot.api.service;

import dev.campuspilot.api.domain.Subject;
import dev.campuspilot.api.exception.ConflictException;
import dev.campuspilot.api.exception.NotFoundException;
import dev.campuspilot.api.repository.SubjectRepository;
import dev.campuspilot.api.web.dto.CreateSubjectRequest;
import dev.campuspilot.api.web.dto.SubjectResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class SubjectService {

    private static final BigDecimal DEFAULT_TARGET = new BigDecimal("75.00");
    private static final int DEFAULT_REMAINING = 20;

    private final SubjectRepository subjects;

    public SubjectService(SubjectRepository subjects) {
        this.subjects = subjects;
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> listFor(UUID profileId) {
        return subjects.findByProfileIdOrderByNameAsc(profileId).stream()
                .map(SubjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubjectResponse getFor(UUID profileId, UUID subjectId) {
        return SubjectResponse.from(requireOwned(profileId, subjectId));
    }

    @Transactional
    public SubjectResponse create(UUID profileId, CreateSubjectRequest request) {
        // Normalise first, then check. Checking the raw input would let
        // "DBMS" and "DBMS " both pass the duplicate check and only be
        // caught later by the database constraint.
        String name = request.name().trim();

        // Friendly check first. The database unique constraint remains the
        // real guarantee, because two concurrent requests can both pass
        // this check before either commits. Pre-checking buys a better
        // error message; it does not replace the constraint.
        if (subjects.existsByProfileIdAndNameIgnoreCase(profileId, name)) {
            throw new ConflictException("You already have a subject named '" + name + "'");
        }

        Subject s = new Subject();
        s.setProfileId(profileId);
        s.setName(name);
        s.setCode(request.code());
        s.setSemester(request.semester());
        s.setTargetAttendance(
                request.targetAttendance() == null ? DEFAULT_TARGET : request.targetAttendance());
        s.setRemainingClassesEstimate(
                request.remainingClassesEstimate() == null ? DEFAULT_REMAINING
                        : request.remainingClassesEstimate());

        return SubjectResponse.from(subjects.save(s));
    }

    @Transactional
    public SubjectResponse update(UUID profileId, UUID subjectId, CreateSubjectRequest request) {
        Subject s = requireOwned(profileId, subjectId);
        String name = request.name().trim();

        if (!s.getName().equalsIgnoreCase(name)
                && subjects.existsByProfileIdAndNameIgnoreCase(profileId, name)) {
            throw new ConflictException("You already have a subject named '" + name + "'");
        }

        s.setName(name);
        if (request.code() != null) {
            s.setCode(request.code());
        }
        s.setSemester(request.semester());
        if (request.targetAttendance() != null) {
            s.setTargetAttendance(request.targetAttendance());
        }
        if (request.remainingClassesEstimate() != null) {
            s.setRemainingClassesEstimate(request.remainingClassesEstimate());
        }

        return SubjectResponse.from(subjects.save(s));
    }

    @Transactional
    public void delete(UUID profileId, UUID subjectId) {
        Subject s = requireOwned(profileId, subjectId);
        // Attendance rows go with it via ON DELETE CASCADE in the schema.
        subjects.delete(s);
    }

    /**
     * Ownership is enforced here, in the query, rather than by loading
     * the row and comparing ids in Java. findByIdAndProfileId returns
     * empty for someone else's subject, so a caller who guesses a valid
     * id gets a 404 — not a 403, which would confirm the id exists.
     */
    private Subject requireOwned(UUID profileId, UUID subjectId) {
        return subjects.findByIdAndProfileId(subjectId, profileId)
                .orElseThrow(() -> NotFoundException.subject(subjectId));
    }
}