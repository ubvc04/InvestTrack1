package com.examly.springapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record InvestmentInquiryDTO(
        Long inquiryId,
        UserResponseDTO user,
        @NotNull InvestmentDTO investment,
        @NotBlank @Size(max = 4000) String message,
        @Size(max = 255) String subject,
        String status,
        @NotBlank String priority,
        LocalDateTime inquiryDate,
        LocalDateTime responseDate,
        String adminResponse,
        String contactDetails) {
}
