package com.main.nexus.dto;

import com.main.nexus.model.enums.InterestStatus;
import com.main.nexus.model.enums.StatusMatch;
import java.time.LocalDateTime;

public record MatchResponseDTO(
        Long id,
        Double matchScore,
        InterestStatus companyStatus,
        InterestStatus professionalStatus,
        StatusMatch status,
        LocalDateTime createdAt,
        ProjectResponseDTO project,
        ProfessionalSummaryDTO professional
) {}
