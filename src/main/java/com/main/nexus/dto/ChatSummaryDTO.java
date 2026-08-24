package com.main.nexus.dto;

import java.time.LocalDateTime;

public record ChatSummaryDTO(
        Long matchId,
        String otherPartyName,
        String otherPartyPhotoUrl,
        String otherPartyType,
        String projectTitle,
        String lastMessage,
        LocalDateTime lastMessageAt,
        long unreadCount,
        Boolean matchActive,
        Integer daysUntilExpiration,
        String otherPartyCompanyType) {
}
