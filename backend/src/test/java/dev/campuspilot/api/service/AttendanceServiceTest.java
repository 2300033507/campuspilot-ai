package dev.campuspilot.api.service;

import dev.campuspilot.api.domain.AttendanceRecord;
import dev.campuspilot.api.domain.Subject;
import dev.campuspilot.api.exception.ConflictException;
import dev.campuspilot.api.exception.ForbiddenException;
import dev.campuspilot.api.exception.NotFoundException;
import dev.campuspilot.api.repository.AttendanceRecordRepository;
import dev.campuspilot.api.repository.EnrollmentRepository;
import dev.campuspilot.api.repository.RosterQueryRepository;
import dev.campuspilot.api.security.AuthenticatedProfile;
import dev.campuspilot.api.web.dto.MarkAttendanceRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * THE MOST IMPORTANT TESTS IN THE PROJECT.
 *
 * <p>Every other test here checks that something computes correctly.
 * These check that a student cannot award themselves perfect attendance.
 * A bug in these would make the whole product meaningless while every
 * other test still passed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttendanceService — who may record attendance")
class AttendanceServiceTest {

    @Mock
    AttendanceRecordRepository records;
    @Mock
    EnrollmentRepository enrollments;
    @Mock
    SubjectService subjectService;
    @Mock
    RosterQueryRepository rosterRepository;

    @InjectMocks
    AttendanceService service;

    private static final UUID SUBJECT = UUID.randomUUID();
    private static final UUID STUDENT = UUID.randomUUID();
    private static final UUID OTHER_TEACHER = UUID.randomUUID();

    private final AuthenticatedProfile student =
            new AuthenticatedProfile(STUDENT, "student");
    private final AuthenticatedProfile teacher =
            new AuthenticatedProfile(UUID.randomUUID(), "faculty");
    private final AuthenticatedProfile admin =
            new AuthenticatedProfile(UUID.randomUUID(), "admin");

    private final MarkAttendanceRequest present =
            new MarkAttendanceRequest(LocalDate.of(2026, 10, 15), "present", null);

    private Subject subjectOwnedBy(AuthenticatedProfile owner) {
        Subject s = new Subject();
        s.setId(SUBJECT);
        s.setName("DBMS");
        s.setSemester(5);
        s.setFacultyId(owner.profileId());
        return s;
    }

    // ---------- the core rule ----------

    @Test
    @DisplayName("a student cannot mark their own attendance, even with a valid token")
    void studentCannotMark() {
        assertThatThrownBy(() -> service.mark(student, SUBJECT, STUDENT, present))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("faculty");

        // Nothing was written, and nothing was even looked up. The role
        // check happens before the repository is touched.
        verify(records, never()).save(any());
    }

    @Test
    @DisplayName("a student cannot mark a CLASSMATE's attendance either")
    void studentCannotMarkForAnyone() {
        assertThatThrownBy(() ->
                service.mark(student, SUBJECT, OTHER_TEACHER, present))
                .isInstanceOf(ForbiddenException.class);

        verify(records, never()).save(any());
    }

    @Test
    @DisplayName("a student cannot delete an attendance record")
    void studentCannotDelete() {
        assertThatThrownBy(() ->
                service.delete(student, SUBJECT, UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);

        verify(records, never()).delete(any());
    }

    @Test
    @DisplayName("a student cannot read the class roster")
    void studentCannotReadRoster() {
        assertThatThrownBy(() -> service.roster(student, SUBJECT))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---------- faculty can ----------

    @Test
    @DisplayName("the teacher of the subject records attendance")
    void teacherCanMark() {
        when(subjectService.requireOwnedByCaller(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(SUBJECT)))
                .thenReturn(subjectOwnedBy(teacher));
        when(enrollments.isEnrolled(SUBJECT, STUDENT)).thenReturn(true);
        when(records.existsBySubjectIdAndProfileIdAndHeldOn(SUBJECT, STUDENT, present.heldOn()))
                .thenReturn(false);
        when(records.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.mark(teacher, SUBJECT, STUDENT, present);

        assertThat(result.status()).isEqualTo("present");
        assertThat(result.profileId()).isEqualTo(STUDENT);
    }

    @Test
    @DisplayName("the record is stamped with who marked it")
    void stampsTheMarker() {
        when(subjectService.requireOwnedByCaller(any(), org.mockito.ArgumentMatchers.eq(SUBJECT)))
                .thenReturn(subjectOwnedBy(teacher));
        when(enrollments.isEnrolled(SUBJECT, STUDENT)).thenReturn(true);
        when(records.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        service.mark(teacher, SUBJECT, STUDENT, present);

        var captor = org.mockito.ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(records).save(captor.capture());

        // Traceability: a disputed mark should be arguable against a
        // record, not against a rumour.
        assertThat(captor.getValue().getMarkedBy())
                .as("a disputed attendance mark must name the person who made it")
                .isEqualTo(teacher.profileId());
    }

    @Test
    @DisplayName("an admin may record attendance")
    void adminCanMark() {
        when(subjectService.requireOwnedByCaller(any(), org.mockito.ArgumentMatchers.eq(SUBJECT)))
                .thenReturn(subjectOwnedBy(admin));
        when(enrollments.isEnrolled(SUBJECT, STUDENT)).thenReturn(true);
        when(records.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.mark(admin, SUBJECT, STUDENT, present).status()).isEqualTo("present");
    }

    // ---------- faculty boundaries ----------

    @Test
    @DisplayName("a teacher cannot mark attendance for someone not enrolled")
    void refusesNonEnrolledStudent() {
        when(subjectService.requireOwnedByCaller(any(), org.mockito.ArgumentMatchers.eq(SUBJECT)))
                .thenReturn(subjectOwnedBy(teacher));
        when(enrollments.isEnrolled(SUBJECT, STUDENT)).thenReturn(false);

        assertThatThrownBy(() -> service.mark(teacher, SUBJECT, STUDENT, present))
                .isInstanceOf(NotFoundException.class);

        // Silently creating a record for a non-student would produce rows
        // nothing reads and quietly distort the class average.
        verify(records, never()).save(any());
    }

    @Test
    @DisplayName("a teacher cannot mark the same day twice")
    void refusesDuplicate() {
        when(subjectService.requireOwnedByCaller(any(), org.mockito.ArgumentMatchers.eq(SUBJECT)))
                .thenReturn(subjectOwnedBy(teacher));
        when(enrollments.isEnrolled(SUBJECT, STUDENT)).thenReturn(true);
        when(records.existsBySubjectIdAndProfileIdAndHeldOn(SUBJECT, STUDENT, present.heldOn()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.mark(teacher, SUBJECT, STUDENT, present))
                .isInstanceOf(ConflictException.class);

        verify(records, never()).save(any());
    }

    @Test
    @DisplayName("a teacher cannot mark a subject they do not teach")
    void refusesForeignSubject() {
        // SubjectService throws; this service must let it, rather than
        // duplicating the ownership rule in a second place.
        when(subjectService.requireOwnedByCaller(any(), org.mockito.ArgumentMatchers.eq(SUBJECT)))
                .thenThrow(ForbiddenException.notYourSubject());

        assertThatThrownBy(() -> service.mark(teacher, SUBJECT, STUDENT, present))
                .isInstanceOf(ForbiddenException.class);

        verify(records, never()).save(any());
    }

    @Test
    @DisplayName("a record from another subject cannot be deleted through this one")
    void refusesForeignRecord() {
        when(subjectService.requireOwnedByCaller(any(), org.mockito.ArgumentMatchers.eq(SUBJECT)))
                .thenReturn(subjectOwnedBy(teacher));

        AttendanceRecord elsewhere = new AttendanceRecord();
        elsewhere.setId(UUID.randomUUID());
        elsewhere.setSubjectId(UUID.randomUUID());   // a different subject

        when(records.findById(elsewhere.getId())).thenReturn(Optional.of(elsewhere));

        // Record ids are caller-supplied, so existence alone is not enough.
        assertThatThrownBy(() ->
                service.delete(teacher, SUBJECT, elsewhere.getId()))
                .isInstanceOf(ConflictException.class);

        verify(records, never()).delete(any());
    }

    // ---------- read scoping ----------

    @Test
    @DisplayName("a student reads only their own records")
    void studentReadsOnlyOwn() {
        when(subjectService.requireVisible(student, SUBJECT)).thenReturn(subjectOwnedBy(teacher));
        when(records.findBySubjectIdAndProfileIdOrderByHeldOnDesc(SUBJECT, STUDENT))
                .thenReturn(java.util.List.of());

        service.listOwn(student, SUBJECT);

        // Scoped by the CALLER's id, never by a parameter, so there is no
        // way to ask for someone else's records at all.
        verify(records).findBySubjectIdAndProfileIdOrderByHeldOnDesc(SUBJECT, STUDENT);
        verify(records, never())
                .findBySubjectIdAndProfileIdOrderByHeldOnDesc(
                        org.mockito.ArgumentMatchers.eq(SUBJECT),
                        org.mockito.ArgumentMatchers.eq(OTHER_TEACHER));
    }
}