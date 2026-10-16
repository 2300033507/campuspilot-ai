package dev.campuspilot.api.service;

import dev.campuspilot.api.domain.Subject;
import dev.campuspilot.api.exception.ConflictException;
import dev.campuspilot.api.exception.ForbiddenException;
import dev.campuspilot.api.exception.NotFoundException;
import dev.campuspilot.api.repository.EnrollmentRepository;
import dev.campuspilot.api.repository.SubjectRepository;
import dev.campuspilot.api.security.AuthenticatedProfile;
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
    private final EnrollmentRepository enrollments;

    public SubjectService(SubjectRepository subjects, EnrollmentRepository enrollments) {
        this.subjects = subjects;
        this.enrollments = enrollments;
    }

    /**
     * What this caller may see, which depends entirely on their role.
     *
     * <p>A student sees the offerings they are enrolled in. A teacher sees
     * the ones they teach. An admin sees everything. The rule is chosen
     * here, once, rather than at each call site.
     */
    @Transactional(readOnly = true)
    public List<SubjectResponse> listVisibleTo(AuthenticatedProfile caller) {
        List<Subject> found = switch (role(caller)) {
            case "admin"    -> subjects.findAllByOrderByNameAsc();
            case "faculty"  -> subjects.findByFacultyIdOrderByNameAsc(caller.profileId());
            default         -> subjects.findEnrolledBy(caller.profileId());
        };
        return found.stream().map(SubjectResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SubjectResponse get(AuthenticatedProfile caller, UUID subjectId) {
        return SubjectResponse.from(requireVisible(caller, subjectId));
    }

    /**
     * Creating an offering requires a teacher or admin. A student creating
     * their own subject would produce a class nobody teaches.
     */
    @Transactional
    public SubjectResponse create(AuthenticatedProfile caller, CreateSubjectRequest request) {
        if (!caller.canMarkAttendance()) {
            throw ForbiddenException.facultyOnly();
        }

        String name = request.name().trim();
        String code = request.code() == null ? null : request.code().trim();

        if (code != null && subjects.existsByNameIgnoreCaseAndCode(name, code)) {
            throw new ConflictException("An offering with that name and code already exists");
        }

        Subject s = new Subject();
        s.setFacultyId(caller.isAdmin() ? request.facultyId() : caller.profileId());
        s.setName(name);
        s.setCode(code);
        s.setSemester(request.semester());
        s.setTargetAttendance(request.targetAttendance() == null
                ? DEFAULT_TARGET : request.targetAttendance());
        s.setRemainingClassesEstimate(request.remainingClassesEstimate() == null
                ? DEFAULT_REMAINING : request.remainingClassesEstimate());

        return SubjectResponse.from(subjects.save(s));
    }

    /** Only the teacher who owns the offering may change it, or an admin. */
    @Transactional
    public SubjectResponse update(AuthenticatedProfile caller, UUID subjectId,
                                  CreateSubjectRequest request) {
        Subject s = requireOwnedByCaller(caller, subjectId);

        s.setName(request.name().trim());
        if (request.code() != null) {
            s.setCode(request.code().trim());
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
    public void delete(AuthenticatedProfile caller, UUID subjectId) {
        Subject s = requireOwnedByCaller(caller, subjectId);

        long enrolled = enrollments.countBySubjectId(subjectId);
        if (enrolled > 0 && !caller.isAdmin()) {
            // Deleting would cascade away every attendance record for
            // every student in the class. That should be a deliberate
            // admin action, not a stray click by a teacher.
            throw new ConflictException(
                    "Cannot delete a subject with " + enrolled
                  + " enrolled student(s). Ask an admin.");
        }
        subjects.delete(s);
    }

    // --- access helpers ------------------------------------------------

    private static String role(AuthenticatedProfile caller) {
        return caller.role() == null ? "student" : caller.role();
    }

    /** The subject, if this caller is allowed to see it at all. */
    Subject requireVisible(AuthenticatedProfile caller, UUID subjectId) {
        return switch (role(caller)) {
            case "admin" -> subjects.findById(subjectId)
                    .orElseThrow(() -> NotFoundException.subject(subjectId));
            case "faculty" -> subjects.findByIdAndFacultyId(subjectId, caller.profileId())
                    .orElseThrow(ForbiddenException::notYourSubject);
            default -> subjects.findEnrolledById(subjectId, caller.profileId())
                    .orElseThrow(() -> NotFoundException.subject(subjectId));
        };
    }

    /** The subject, but only if this caller teaches it (or is admin). */
    Subject requireOwnedByCaller(AuthenticatedProfile caller, UUID subjectId) {
        if (caller.isAdmin()) {
            return subjects.findById(subjectId)
                    .orElseThrow(() -> NotFoundException.subject(subjectId));
        }
        return subjects.findByIdAndFacultyId(subjectId, caller.profileId())
                .orElseThrow(ForbiddenException::notYourSubject);
    }
}