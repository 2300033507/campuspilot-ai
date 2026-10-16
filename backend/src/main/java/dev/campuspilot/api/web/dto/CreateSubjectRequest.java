package dev.campuspilot.api.web.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for creating a subject.
 *
 * <p>Validation annotations here duplicate constraints that already exist
 * in PostgreSQL. That is intentional and worth explaining: the database
 * constraint is the real guarantee, this one exists to return a helpful
 * 400 with a field name instead of a driver error. Two layers, different
 * jobs — never rely on only one.
 */
public record CreateSubjectRequest(

        @NotBlank(message = "Subject name is required")
        @Size(max = 120, message = "Subject name must be under 120 characters")
        String name,

        @Pattern(regexp = "^[A-Z0-9]{2,12}$",
                 message = "Code must be 2-12 uppercase letters or digits, e.g. CS501")
        String code,

        @NotNull(message = "Semester is required")
        @Min(value = 1, message = "Semester must be between 1 and 10")
        @Max(value = 10, message = "Semester must be between 1 and 10")
        Integer semester,

        @DecimalMin(value = "0.0", message = "Target cannot be below 0")
        @DecimalMax(value = "100.0", message = "Target cannot exceed 100")
        BigDecimal targetAttendance,

        @Min(value = 0, message = "Remaining classes cannot be negative")
        @Max(value = 200, message = "Remaining classes cannot exceed 200")
        Integer remainingClassesEstimate,

        /**
         * Only honoured for an admin creating an offering on a teacher's
         * behalf. A faculty member assigning it to themselves would
         * create a class they happen to teach, which is not the point.
         */
        UUID facultyId
) {
}