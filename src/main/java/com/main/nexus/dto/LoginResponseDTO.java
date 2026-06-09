
package com.main.nexus.dto;

public record LoginResponseDTO(
        Long userId,
        String email,
        String role,
        String token
) {}