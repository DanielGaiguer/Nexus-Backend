package com.main.nexus.dto;

public record PendingStatusCheckDTO(
        Long matchId,
        String professionalName,
        String projectTitle
) {}
