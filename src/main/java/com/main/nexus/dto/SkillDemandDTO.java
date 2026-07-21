package com.main.nexus.dto;

public record SkillDemandDTO(
        String skillName,
        String category,
        long projectCount
) {}