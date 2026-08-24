package com.main.nexus.dto;

import com.main.nexus.model.enums.OpportunityType;
import java.time.LocalDateTime;

public record CompanyPreviousProjectDTO(
        Long id,
        Long projectId,
        String projectTitle,
        OpportunityType opportunityType,
        LocalDateTime completedAt
) {}
