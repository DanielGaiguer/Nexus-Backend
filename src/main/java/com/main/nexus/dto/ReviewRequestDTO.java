
package com.main.nexus.dto;

import com.main.nexus.model.enums.AuthorType;

public record ReviewRequestDTO(
        Integer rating,
        String comment,
        AuthorType authorType
) {}