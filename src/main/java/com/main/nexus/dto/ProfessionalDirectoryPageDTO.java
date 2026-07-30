package com.main.nexus.dto;

import java.util.List;

public record ProfessionalDirectoryPageDTO(
        List<ProfessionalDirectoryItemDTO> content,
        boolean hasMore
) {}
