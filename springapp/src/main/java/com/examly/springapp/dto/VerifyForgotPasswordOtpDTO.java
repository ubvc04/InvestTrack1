package com.examly.springapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyForgotPasswordOtpDTO(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "\\d{6}") String otp) {
}
