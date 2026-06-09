
package com.main.nexus.dto;

public record UserSummaryDTO(
        Long id,
        String email,
        String type,
        Boolean active
) {}