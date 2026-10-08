package com.examly.springapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequestDTO(
        @NotBlank String oldPassword,
        @NotBlank @Size(min = 8, max = 100) String newPassword,
        @NotBlank String confirmPassword) {
}
