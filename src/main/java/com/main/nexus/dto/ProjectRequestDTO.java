
package com.main.nexus.dto;

import com.main.nexus.model.enums.Modality;
import java.time.LocalDate;
import java.util.List;

public record ProjectRequestDTO(
        String title,
        String description,
        Double minimumBudget,
        Double maximumBudget,
        LocalDate deadline,
        Modality workMode,
        List<Long> skillIds
) {}