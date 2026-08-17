package com.main.nexus.dto;

import com.main.nexus.model.enums.ProfessionalRejectionReason;
import java.util.List;

public record ProfessionalRejectRequestDTO(
        List<ProfessionalRejectionReason> reasons,
        String description
) {}
