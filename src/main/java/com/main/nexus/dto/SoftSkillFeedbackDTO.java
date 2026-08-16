package com.main.nexus.dto;

import com.main.nexus.model.enums.NegativeReason;

// Frequência de cada motivo negativo apontado em avaliações recebidas — usado no card
// "SoftSkills" do dashboard (tanto do profissional quanto da empresa).
public record SoftSkillFeedbackDTO(
        String reason,
        String enumValue,
        long count
) {

    public static SoftSkillFeedbackDTO of(NegativeReason reason, long count) {
        return new SoftSkillFeedbackDTO(label(reason), reason.name(), count);
    }

    // Mapeamento único do enum para o nome legível em português — usado em todos os
    // lugares do código que precisem exibir um NegativeReason (fonte única de verdade).
    private static String label(NegativeReason reason) {
        return switch (reason) {
            case MISSED_DEADLINES -> "Cumprimento de Prazos";
            case POOR_COMMUNICATION -> "Comunicação";
            case LOW_CODE_QUALITY -> "Qualidade Técnica";
            case UNPROFESSIONAL -> "Profissionalismo";
            case ABSENT -> "Presença e Disponibilidade";
            case UNRELIABLE -> "Confiabilidade";
            case POOR_PROBLEM_SOLVING -> "Resolução de Problemas";
            case DID_NOT_MEET_EXPECTATIONS -> "Atendimento às Expectativas";
            case OTHER -> "Outros";
        };
    }
}
