package dev.campuspilot.api.web.dto;

import dev.campuspilot.api.domain.Subject;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Built from the entity through a static factory so the JSON shape is
 * decided in exactly one place. Returning entities directly would expose
 * every column, including ones added later.
 */
public record SubjectResponse(
        UUID id,
        String name,
        String code,
        Integer semester,
        BigDecimal targetAttendance,
        Integer remainingClassesEstimate,
        /** Null when the offering is not yet assigned to a teacher. */
        UUID facultyId
) {

    public static SubjectResponse from(Subject s) {
        return new SubjectResponse(
                s.getId(),
                s.getName(),
                s.getCode(),
                s.getSemester(),
                s.getTargetAttendance(),
                s.getRemainingClassesEstimate(),
                s.getFacultyId()
        );
    }
}