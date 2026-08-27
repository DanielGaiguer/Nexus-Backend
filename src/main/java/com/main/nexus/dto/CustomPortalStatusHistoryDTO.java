package com.main.nexus.dto;

import com.main.nexus.model.CustomPortalStatusHistory;
import java.time.LocalDateTime;

public record CustomPortalStatusHistoryDTO(
        Long id,
        String previousStatus,
        String newStatus,
        String changedByEmail,
        LocalDateTime changedAt,
        String note
) {
    public static CustomPortalStatusHistoryDTO from(CustomPortalStatusHistory h) {
        return new CustomPortalStatusHistoryDTO(
                h.getId(),
                h.getPreviousStatus() != null ? h.getPreviousStatus().name() : null,
                h.getNewStatus().name(),
                h.getChangedBy() != null ? h.getChangedBy().getEmail() : null,
                h.getChangedAt(),
                h.getNote()
        );
    }
}
