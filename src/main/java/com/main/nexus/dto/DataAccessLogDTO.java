package com.main.nexus.dto;

import com.main.nexus.model.DataAccessLog;
import java.time.LocalDateTime;

// Uma linha do log de auditoria de acesso administrativo a dado pessoal.
public record DataAccessLogDTO(
        Long id,
        Long adminUserId,
        String adminEmail,
        Long targetUserId,
        String targetUserEmail,
        String targetType,
        Long targetEntityId,
        String action,
        String httpMethod,
        String endpoint,
        LocalDateTime at
) {
    public static DataAccessLogDTO from(DataAccessLog l) {
        return new DataAccessLogDTO(
                l.getId(),
                l.getAdminUser() != null ? l.getAdminUser().getId() : null,
                l.getAdminUser() != null ? l.getAdminUser().getEmail() : null,
                l.getTargetUser() != null ? l.getTargetUser().getId() : null,
                l.getTargetUser() != null ? l.getTargetUser().getEmail() : null,
                l.getTargetType() != null ? l.getTargetType().name() : null,
                l.getTargetEntityId(),
                l.getAction(),
                l.getHttpMethod(),
                l.getEndpoint(),
                l.getCreatedAt());
    }
}
