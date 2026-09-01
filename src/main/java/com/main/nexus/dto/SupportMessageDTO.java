package com.main.nexus.dto;

import java.time.LocalDateTime;

// Uma mensagem de suporte para as telas. `senderRole` = "ADMIN" | "PROFESSIONAL" | "COMPANY".
public record SupportMessageDTO(
        Long id,
        Long conversationId,
        Long senderId,
        String senderName,
        String senderRole,
        String senderPhotoUrl,
        String content,
        LocalDateTime sentAt,
        Boolean read
) {}
