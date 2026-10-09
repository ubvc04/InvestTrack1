package com.examly.springapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record FeedbackDTO(
        Long feedbackId,
        @NotBlank @Size(max = 4000) String feedbackText,
        String date,
        UserResponseDTO user,
        InvestmentDTO investment,
        @NotBlank String category,
        @Size(max = 255) String subject,
        String adminResponse,
        LocalDateTime responseDate) {
}
