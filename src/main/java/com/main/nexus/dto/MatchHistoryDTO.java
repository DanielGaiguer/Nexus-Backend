package com.main.nexus.dto;

import java.time.LocalDateTime;

public record MatchHistoryDTO(
        String fromStatus,
        String toStatus,
        String changedBy,
        LocalDateTime changedAt
) {}
