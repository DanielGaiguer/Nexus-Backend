package com.main.nexus.dto;

import java.time.LocalDateTime;

// Resumo de uma conversa de suporte (lista + cabecalho da janela). Serve os dois
// lados: o Admin ve `userName`/`userRole`; o usuario ignora esses e ve "Suporte Nexus".
public record SupportConversationDTO(
        Long id,
        Long userId,
        String userName,
        String userRole,           // "PROFESSIONAL" | "COMPANY"
        String userPhotoUrl,
        String subject,
        String status,             // "OPEN" | "CLOSED"
        String openedByAdminEmail,
        boolean openedByUser,      // true = aberta pelo usuário, não pelo Admin
        LocalDateTime createdAt,
        LocalDateTime closedAt,
        String lastMessage,
        LocalDateTime lastMessageAt,
        long unreadCount
) {}
