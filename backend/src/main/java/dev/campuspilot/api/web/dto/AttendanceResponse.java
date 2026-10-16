package dev.campuspilot.api.web.dto;

import dev.campuspilot.api.domain.AttendanceRecord;

import java.time.LocalDate;
import java.util.UUID;

public record AttendanceResponse(
        UUID id,
        UUID subjectId,
        LocalDate heldOn,
        String status,
        String note
) {

    public static AttendanceResponse from(AttendanceRecord r) {
        return new AttendanceResponse(
                r.getId(),
                r.getSubjectId(),
                r.getHeldOn(),
                r.getStatus(),
                r.getNote()
        );
    }
}