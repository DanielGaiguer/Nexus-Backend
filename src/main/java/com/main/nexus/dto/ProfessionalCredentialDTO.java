package com.main.nexus.dto;

import com.main.nexus.model.enums.BadgeColor;
import com.main.nexus.model.enums.CredentialType;

public record ProfessionalCredentialDTO(
        Long id,
        CredentialType type,
        String name,
        BadgeColor color
) {}
