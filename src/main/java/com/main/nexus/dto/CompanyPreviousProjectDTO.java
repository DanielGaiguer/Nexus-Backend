package com.main.nexus.dto;

import java.time.LocalDateTime;

public record CompanyPreviousProjectDTO(
        Long id,
        String projectTitle,
        LocalDateTime completedAt
) {}
