package com.main.nexus.dto;

import com.main.nexus.model.enums.CustomPortalEventType;

// Corpo de POST /api/public/custom-portal/{subdomain}/events — enviado pelo
// browser anônimo na página pública. Tudo opcional exceto visitorId e type.
public record TrackPortalEventDTO(
        String visitorId,
        CustomPortalEventType type,
        String path,
        Long opportunityId,
        Integer durationSeconds,
        String referrerHost
) {}
