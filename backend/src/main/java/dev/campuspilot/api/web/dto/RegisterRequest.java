package dev.campuspilot.api.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "That does not look like a valid email address")
        @Size(max = 255, message = "Email must be under 255 characters")
        String email,

        /**
         * 10 characters minimum is not decoration. Length is what
         * actually resists guessing; symbol rules mostly produce
         * Password1! and push users toward writing it on a sticky note.
         */
        @NotBlank(message = "Password is required")
        @Size(min = 10, max = 100, message = "Password must be at least 10 characters")
        @Pattern(regexp = "^.*[A-Za-z0-9].*$",
                 message = "Password must contain at least one letter or digit")
        String password
) {
}