package com.examly.springapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Administrative response to a feedback entry. */
public record RespondToFeedbackDTO(
        @NotBlank @Size(max = 4000) String adminResponse) {
}
