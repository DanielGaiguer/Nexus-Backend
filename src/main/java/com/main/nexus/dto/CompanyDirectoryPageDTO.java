package com.main.nexus.dto;

import java.util.List;

public record CompanyDirectoryPageDTO(
        List<CompanyDirectoryItemDTO> content,
        boolean hasMore
) {}
