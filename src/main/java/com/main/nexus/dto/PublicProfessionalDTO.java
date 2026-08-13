package com.main.nexus.dto;

import java.util.List;

public record PublicProfessionalDTO(
        Long id,
        String name,
        String city,
        String uf,
        String experienceLevel,
        Double reputation,
        Boolean available,
        List<String> skills,
        List<PublicProjectDTO> previousProjects,
        Double overallScore,
        ReputationExplanationDTO reputationDetails,
        String profilePhotoUrl,
        Double minimumSalary,
        Double maximumSalary,
        List<String> preferredTypes,

        String githubUrl,
        String githubLogin,
        boolean hasGitHub
) {}
