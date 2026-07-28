
package com.main.nexus.dto;

import java.util.List;

public record PreviousProjectRequestDTO(
        Long id,
        String title,
        String description,
        List<String> technologies,
        Integer yearOfCompletion
) {}