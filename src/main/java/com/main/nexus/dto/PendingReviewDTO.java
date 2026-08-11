package com.main.nexus.dto;

public record PendingReviewDTO(
        Long matchId,
        String otherPartyName,
        String projectTitle
) {}
