package dev.campuspilot.api.web.dto;

import dev.campuspilot.api.domain.AttendanceRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AttendanceResponse(
        UUID id,
        UUID subjectId,
        UUID profileId,
        LocalDate heldOn,
        String status,
        String note,
        /** Who recorded it. Null on rows migrated before faculty marking. */
        UUID markedBy,
        Instant markedAt
) {

    public static AttendanceResponse from(AttendanceRecord r) {
        return new AttendanceResponse(
                r.getId(),
                r.getSubjectId(),
                r.getProfileId(),
                r.getHeldOn(),
                r.getStatus(),
                r.getNote(),
                r.getMarkedBy(),
                r.getMarkedAt()
        );
    }
}