package com.examly.springapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordDTO(
        @NotBlank @Email String email,
        @NotBlank String resetToken,
        @NotBlank @Size(min = 6, max = 100) String newPassword,
        @NotBlank String confirmPassword) {
}
