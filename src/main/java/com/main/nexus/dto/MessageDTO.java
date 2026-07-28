package com.main.nexus.dto;

import java.time.LocalDateTime;

public record MessageDTO(
        Long id,
        Long matchId,
        Long senderId,
        String senderName,
        String senderType,
        String senderPhotoUrl,
        String content,
        LocalDateTime sentAt,
        Boolean read) {
}
