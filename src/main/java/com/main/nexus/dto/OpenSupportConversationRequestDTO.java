package com.main.nexus.dto;

// Corpo de POST /api/admin/support/conversations -- o Admin abre uma conversa
// com um usuario. `subject` e `message` sao opcionais.
public record OpenSupportConversationRequestDTO(
        Long userId,
        String subject,
        String message
) {}
