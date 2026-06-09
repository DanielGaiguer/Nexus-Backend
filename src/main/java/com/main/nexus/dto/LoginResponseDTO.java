
package com.main.nexus.dto;

public record LoginResponseDTO(
        Long userId,
        String email,
        String name,
        String role,
        String token
) {}