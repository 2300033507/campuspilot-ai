package dev.campuspilot.api.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "That does not look like a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {
}