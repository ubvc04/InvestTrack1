package com.examly.springapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyPhoneOtpDTO(
        @NotBlank @Pattern(regexp = "^[+]?[0-9 ()-]{10,20}$") String phoneNumber,
        @NotBlank @Pattern(regexp = "\\d{6}") String otp) {
}
