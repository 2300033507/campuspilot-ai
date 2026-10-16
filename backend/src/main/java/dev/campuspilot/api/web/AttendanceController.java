package dev.campuspilot.api.web;

import dev.campuspilot.api.config.CurrentProfileResolver;
import dev.campuspilot.api.projection.RosterEntry;
import dev.campuspilot.api.service.AttendanceService;
import dev.campuspilot.api.web.dto.AttendanceResponse;
import dev.campuspilot.api.web.dto.MarkAttendanceRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Attendance endpoints.
 *
 * <p>GET is open to any caller who can see the subject — a student sees
 * only their own rows.
 *
 * <p>POST and DELETE require faculty or admin, AND require that caller to
 * teach this subject. A student calling POST directly with a valid token
 * gets 403, not a hidden button.
 */
@RestController
@RequestMapping("/api/subjects/{subjectId}/attendance")
public class AttendanceController {

    private final AttendanceService attendance;
    private final CurrentProfileResolver currentProfile;

    public AttendanceController(AttendanceService attendance,
                                CurrentProfileResolver currentProfile) {
        this.attendance = attendance;
        this.currentProfile = currentProfile;
    }

    /** The caller's own records for this subject. */
    @GetMapping
    public List<AttendanceResponse> myRecords(@PathVariable UUID subjectId) {
        return attendance.listOwn(currentProfile.resolve(), subjectId);
    }

    /** Faculty: the class roster with each student's standing. */
    @GetMapping("/roster")
    public List<RosterEntry> roster(@PathVariable UUID subjectId) {
        return attendance.roster(currentProfile.requireAttendanceMarker(), subjectId);
    }

    /** Faculty: what has already been recorded for one date. */
    @GetMapping("/roll-call")
    public List<AttendanceResponse> rollCall(
            @PathVariable UUID subjectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attendance.rollCall(currentProfile.requireAttendanceMarker(), subjectId, date);
    }

    /**
     * Faculty records one student.
     *
     * <p>studentId is in the PATH, not optional and not inferred from the
     * token. A teacher legitimately marks many students in one sitting,
     * and making them re-authenticate per student would be unusable.
     */
    @PostMapping("/students/{studentId}")
    public ResponseEntity<AttendanceResponse> mark(
            @PathVariable UUID subjectId,
            @PathVariable UUID studentId,
            @Valid @RequestBody MarkAttendanceRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attendance.mark(currentProfile.requireAttendanceMarker(),
                                      subjectId, studentId, body));
    }

    /** Faculty removes an incorrect mark. */
    @DeleteMapping("/records/{recordId}")
    public ResponseEntity<Void> delete(@PathVariable UUID subjectId,
                                       @PathVariable UUID recordId) {
        attendance.delete(currentProfile.requireAttendanceMarker(), subjectId, recordId);
        return ResponseEntity.noContent().build();
    }
}