
package com.main.nexus.dto;

public record PreviousProjectRequestDTO(
        Long id,
        String title,
        String description,
        String technologies,
        Integer yearOfCompletion
) {}