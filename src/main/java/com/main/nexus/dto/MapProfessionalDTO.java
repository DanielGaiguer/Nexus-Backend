package com.main.nexus.dto;

public record MapProfessionalDTO(
        Long id,
        String name,
        String city,
        String uf,
        Double latitude,
        Double longitude,
        Double reputation,
        String experienceLevel,
        java.util.List<String> skills
) {}