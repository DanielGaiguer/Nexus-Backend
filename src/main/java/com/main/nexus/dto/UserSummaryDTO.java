package com.main.nexus.dto;

public record UserSummaryDTO(
        Long id,
        String name,
        String email,
        String type,
        Boolean active,
        String profilePhotoUrl
) {}
