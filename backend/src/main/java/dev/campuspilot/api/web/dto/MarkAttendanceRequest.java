package dev.campuspilot.api.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Payload for recording one student's attendance in one class.
 *
 * <p>PastOrPresent blocks back-dating into the future. The database has
 * no such rule — that is business logic, so it belongs here.
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