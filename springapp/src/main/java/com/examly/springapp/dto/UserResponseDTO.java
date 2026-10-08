package com.examly.springapp.dto;

public record UserResponseDTO(
        Long userId,
        String username,
        String email,
        String mobileNumber,
        String userRole,
        boolean mustChangePassword) {
}
