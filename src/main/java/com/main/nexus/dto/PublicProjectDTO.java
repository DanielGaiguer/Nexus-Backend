package com.main.nexus.dto;

import java.util.List;

public record PublicProjectDTO(
        String title,
        List<String> technologies,
        Integer yearOfCompletion
) {}
