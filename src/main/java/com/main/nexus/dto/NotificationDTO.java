package com.main.nexus.dto;

import com.main.nexus.model.enums.NotificationType;
import java.time.LocalDateTime;

public record NotificationDTO(
        Long id,
        NotificationType type,
        String title,
        String message,
        String actionUrl,
        Boolean read,
        LocalDateTime createdAt
) {}