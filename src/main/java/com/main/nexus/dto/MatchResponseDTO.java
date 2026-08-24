package com.main.nexus.dto;

import com.main.nexus.model.enums.InterestStatus;
import com.main.nexus.model.enums.StatusMatch;
import java.time.LocalDateTime;
import java.util.List;

public record MatchResponseDTO(
        Long id,
        Double matchScore,
        InterestStatus companyStatus,
        InterestStatus professionalStatus,
        StatusMatch status,
        LocalDateTime createdAt,
        ProjectResponseDTO project,
        ProfessionalSummaryDTO professional,
        ScoreBreakdownDTO scoreBreakdown,
        Boolean active,
        // Preenchidos apenas quando status == REJECTED — motivos selecionados (nomes brutos
        // do enum CompanyRejectionReason/ProfessionalRejectionReason, traduzidos no front) e a
        // observação em texto livre escrita por quem rejeitou.
        List<String> rejectionReasons,
        String rejectionDescription,
        // Preenchido para sempre quando este match foi confirmado via aceite de uma Proposal
        // (ProposalService.acceptProposal) -- fica visível em toda tela que já usa este DTO.
        ProposalResponseDTO acceptedProposal
) {}
