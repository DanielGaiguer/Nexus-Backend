
package com.main.nexus.dto;

import java.util.List;

public record PreviousProjectDTO(
        Long id,
        String title,
        String description,
        List<String> technologies,
        Integer yearOfCompletion
) {}