package dev.campuspilot.api.web;

import dev.campuspilot.api.config.CurrentProfileResolver;
import dev.campuspilot.api.service.AttendanceService;
import dev.campuspilot.api.web.dto.AttendanceResponse;
import dev.campuspilot.api.web.dto.MarkAttendanceRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/subjects/{subjectId}/attendance")
public class AttendanceController {

    private final AttendanceService attendance;
    private final CurrentProfileResolver currentProfile;

    public AttendanceController(AttendanceService attendance, CurrentProfileResolver currentProfile) {
        this.attendance = attendance;
        this.currentProfile = currentProfile;
    }

    @GetMapping
    public List<AttendanceResponse> list(@PathVariable UUID subjectId) {
        return attendance.listFor(currentProfile.resolve(), subjectId);
    }

    @PostMapping
    public ResponseEntity<AttendanceResponse> mark(@PathVariable UUID subjectId,
                                                  @Valid @RequestBody MarkAttendanceRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attendance.mark(currentProfile.resolve(), subjectId, body));
    }

    @DeleteMapping("/{recordId}")
    public ResponseEntity<Void> delete(@PathVariable UUID subjectId,
                                       @PathVariable UUID recordId) {
        attendance.delete(currentProfile.resolve(), subjectId, recordId);
        return ResponseEntity.noContent().build();
    }
}