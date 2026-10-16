package dev.campuspilot.api.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Payload for marking one class as held, present or absent.
 *
 * <p>PastOrPresent blocks back-dating attendance into the future. The
 * database has no such check — that rule is business logic, so it lives
 * in the application.
 */
public record MarkAttendanceRequest(

        @NotNull(message = "Date is required")
        @PastOrPresent(message = "Cannot record attendance for a future class")
        LocalDate heldOn,

        @NotNull(message = "Status is required")
        @Pattern(regexp = "present|absent",
                 message = "Status must be exactly 'present' or 'absent'")
        String status,

        @Size(max = 300, message = "Note must be under 300 characters")
        String note
) {
}