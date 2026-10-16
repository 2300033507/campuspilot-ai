package dev.campuspilot.api.service;

import dev.campuspilot.api.domain.Subject;
import dev.campuspilot.api.exception.ConflictException;
import dev.campuspilot.api.exception.NotFoundException;
import dev.campuspilot.api.repository.SubjectRepository;
import dev.campuspilot.api.web.dto.CreateSubjectRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SubjectService, with the repository mocked out.
 *
 * <p>The test that matters is {@link #anotherStudentsSubjectLooksMissing()}.
 * It is the one worth showing in an interview, because it demonstrates
 * that access control is enforced in the query rather than trusted from
 * the caller.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubjectService")
class SubjectServiceTest {

    @Mock
    SubjectRepository subjects;

    @InjectMocks
    SubjectService service;

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID MALLORY = UUID.randomUUID();
    private static final UUID DBMS_ID = UUID.randomUUID();

    private CreateSubjectRequest request() {
        return new CreateSubjectRequest("DBMS", "CS501", 5, new BigDecimal("75.00"), 20);
    }

    @Test
    @DisplayName("creates a subject owned by the calling profile")
    void createsSubject() {
        when(subjects.existsByProfileIdAndNameIgnoreCase(ALICE, "DBMS")).thenReturn(false);
        when(subjects.save(any(Subject.class))).thenAnswer(inv -> {
            Subject s = inv.getArgument(0);
            s.setId(DBMS_ID);
            return s;
        });

        var result = service.create(ALICE, request());

        assertThat(result.id()).isEqualTo(DBMS_ID);
        assertThat(result.name()).isEqualTo("DBMS");
        assertThat(result.remainingClassesEstimate()).isEqualTo(20);

        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjects).save(captor.capture());
        assertThat(captor.getValue().getProfileId())
                .as("profileId must come from the resolved caller, never from the request body")
                .isEqualTo(ALICE);
    }

    @Test
    @DisplayName("another student's subject looks missing, not forbidden")
    void anotherStudentsSubjectLooksMissing() {
        // The repository scopes by (id, profileId). Mallory asking for
        // Alice's subject returns empty — exactly as it would in production,
        // because findByIdAndProfileId would issue the same SQL.
        when(subjects.findByIdAndProfileId(eq(DBMS_ID), eq(MALLORY))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getFor(MALLORY, DBMS_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(DBMS_ID.toString());

        verify(subjects, never()).save(any());
    }

    @Test
    @DisplayName("rejects a duplicate subject name for the same profile")
    void rejectsDuplicateName() {
        when(subjects.existsByProfileIdAndNameIgnoreCase(ALICE, "DBMS")).thenReturn(true);

        assertThatThrownBy(() -> service.create(ALICE, request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("DBMS");

        verify(subjects, never()).save(any());
    }

    @Test
    @DisplayName("applies defaults when the request omits them")
    void appliesDefaults() {
        when(subjects.existsByProfileIdAndNameIgnoreCase(ALICE, "DBMS")).thenReturn(false);
        when(subjects.save(any(Subject.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateSubjectRequest("DBMS", "CS501", 5, null, null);
        var result = service.create(ALICE, request);

        assertThat(result.targetAttendance())
                .as("75% is the common university minimum")
                .isEqualByComparingTo("75.00");
        assertThat(result.remainingClassesEstimate()).isEqualTo(20);
    }

    @Test
    @DisplayName("trims whitespace from the subject name")
    void trimsName() {
        when(subjects.existsByProfileIdAndNameIgnoreCase(ALICE, "DBMS")).thenReturn(false);
        when(subjects.save(any(Subject.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateSubjectRequest("  DBMS  ", "CS501", 5, null, null);
        var result = service.create(ALICE, request);

        assertThat(result.name()).isEqualTo("DBMS");
    }
}