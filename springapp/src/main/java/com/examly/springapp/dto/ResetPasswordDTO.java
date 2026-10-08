package com.examly.springapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordDTO(
        @NotBlank @Size(min = 6, max = 100) String newPassword,
        @NotBlank String confirmPassword) {
}
