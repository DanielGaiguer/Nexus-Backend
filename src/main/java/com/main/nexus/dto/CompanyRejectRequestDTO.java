package com.main.nexus.dto;

import com.main.nexus.model.enums.CompanyRejectionReason;
import java.util.List;

public record CompanyRejectRequestDTO(
        List<CompanyRejectionReason> reasons,
        String description
) {}
