package dev.campuspilot.api.web.dto;

import dev.campuspilot.api.domain.Subject;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What the client gets back. Built from the entity through a static
 * factory so the JSON shape is decided in exactly one place.
 *
 * <p>Returning entities directly would expose every column, including
 * internal ones added later. DTOs keep that decision deliberate.
 */
public record SubjectResponse(
        UUID id,
        String name,
        String code,
        Integer semester,
        BigDecimal targetAttendance,
        Integer remainingClassesEstimate
) {

    public static SubjectResponse from(Subject s) {
        return new SubjectResponse(
                s.getId(),
                s.getName(),
                s.getCode(),
                s.getSemester(),
                s.getTargetAttendance(),
                s.getRemainingClassesEstimate()
        );
    }
}