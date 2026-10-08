package com.examly.springapp.dto;

public record LoginResponseDTO(
        String token,
        String username,
        String userRole,
        Long userId,
        boolean mustChangePassword) {
}
