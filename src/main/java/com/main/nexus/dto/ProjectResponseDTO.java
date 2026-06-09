
package com.main.nexus.dto;

import com.main.nexus.model.enums.Modality;
import com.main.nexus.model.enums.ProjectStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ProjectResponseDTO(
        Long id,
        String title,
        String description,
        Double minimumBudget,
        Double maximumBudget,
        LocalDate deadline,
        Modality workMode,
        ProjectStatus status,
        LocalDateTime createdAt,
        List<String> requiredSkills,
        Long companyId,
        String companyName
) {}