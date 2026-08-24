package com.main.nexus.dto;

import com.main.nexus.model.enums.ExperienceLevel;
import com.main.nexus.model.enums.ProposalStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Carrega tanto os campos brutos da proposta quanto os campos de comparação (reputação, skills
// compatíveis, projetos anteriores) já prontos pro card da tela de comparação da empresa --
// mesmo formato "flat" que CandidateComparisonItemDTO já usa pro profissional, em vez de aninhar
// ProfessionalSummaryDTO, porque é essencialmente a mesma tela de comparação.
public record ProposalResponseDTO(
        Long id,
        Long projectId,
        String projectTitle,
        Long matchId,

        Long professionalId,
        String professionalName,
        String professionalCity,
        String professionalUf,
        String professionalProfilePhotoUrl,
        ExperienceLevel professionalExperienceLevel,

        Double proposedValue,
        Integer estimatedDays,
        LocalDate proposedStartDate,
        LocalDate proposedDeliveryDate,
        String description,
        String relevantExperience,
        List<SkillResponseDTO> skills,
        String deliverables,
        List<String> executionSteps,
        String paymentTerms,
        Integer validityDays,
        LocalDateTime expiresAt,
        String questionsForCompany,
        List<ProposalAttachmentDTO> attachments,

        ProposalStatus status,
        Double matchScoreAtSubmission,
        Boolean autoRejectedPositionFilled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,

        // Comparação -- reputação/histórico do profissional e compatibilidade de skills com o
        // projeto, mesmos dados já usados no ranking de candidatos (CandidateComparisonService).
        // reputationScore é a nota 1-5 (Professional.reputation), não o score 0-100 do motor de match.
        Double reputationScore,
        Integer totalReviews,
        Integer previousProjectsCount,
        List<String> matchingSkills,
        List<String> missingSkills
) {}
