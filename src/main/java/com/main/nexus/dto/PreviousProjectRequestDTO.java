
package com.main.nexus.dto;

public record PreviousProjectRequestDTO(
        String title,
        String description,
        String technologies,
        Integer yearOfCompletion
) {}