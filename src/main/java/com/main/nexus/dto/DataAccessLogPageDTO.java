package com.main.nexus.dto;

import java.util.List;

public record DataAccessLogPageDTO(
        List<DataAccessLogDTO> content,
        long totalElements,
        int page,
        int size,
        boolean hasMore
) {}
