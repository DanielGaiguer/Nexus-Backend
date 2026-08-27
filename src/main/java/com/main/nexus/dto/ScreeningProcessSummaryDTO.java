package com.main.nexus.dto;

import com.main.nexus.model.enums.OpportunityType;
import com.main.nexus.model.enums.ScreeningInvitationStatus;
import java.time.LocalDateTime;
import java.util.List;

// Um processo seletivo (todas as etapas de UM questionário, pra UM profissional) -- base das
// telas "Processos Seletivos" dos dois lados (ver
// ScreeningInvitationService.getProcessesForProfessional/getProcessesForCompany). currentStatus/
// currentInvitationId refletem a tentativa mais recente entre todas as etapas -- é ela que decide
// pra onde a tela linka e qual selo mostrar.
//
// professionalProfilePhotoUrl/professionalReputation/companyProfilePhotoUrl/opportunityType e
// scoreBreakdown existem só pra deixar o card desta tela visualmente idêntico ao de Matches/
// Propostas (mesmo cabeçalho com avatar, badge de vaga/projeto e índices de compatibilidade).
// scoreBreakdown vem sempre null de ScreeningInvitationService -- quem preenche é o controller
// (MatchService.getScoreBreakdownForCandidate), pra não criar dependência circular entre os
// services (mesmo motivo documentado em ScreeningInvitationController).
public record ScreeningProcessSummaryDTO(
        Long screeningQuestionnaireId,
        Long projectId,
        String projectTitle,
        OpportunityType opportunityType,

        Long professionalId,
        String professionalName,
        String professionalProfilePhotoUrl,
        Double professionalReputation,

        Long companyId,
        String companyName,
        String companyProfilePhotoUrl,

        ScreeningInvitationStatus currentStatus,
        Long currentInvitationId,
        Integer currentStageOrderIndex,
        Integer totalStages,
        LocalDateTime lastActivityAt,

        List<ScreeningStageStatusDTO> stages,

        ScoreBreakdownDTO scoreBreakdown
) {
    public ScreeningProcessSummaryDTO withScoreBreakdown(ScoreBreakdownDTO breakdown) {
        return new ScreeningProcessSummaryDTO(
                screeningQuestionnaireId, projectId, projectTitle, opportunityType,
                professionalId, professionalName, professionalProfilePhotoUrl, professionalReputation,
                companyId, companyName, companyProfilePhotoUrl,
                currentStatus, currentInvitationId, currentStageOrderIndex, totalStages, lastActivityAt,
                stages, breakdown
        );
    }
}
