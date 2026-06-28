
package com.main.nexus.dto;

import com.main.nexus.model.enums.Modality;
import java.time.LocalDate;
import java.util.List;
import com.main.nexus.model.enums.ProjectType;

public record ProjectRequestDTO(
        String title,
        String description,
        Double minimumBudget,
        Double maximumBudget,
        LocalDate deadline,
        Modality workMode,
        ProjectType type,
        Integer maxPositions,
        List<Long> skillIds
) {}