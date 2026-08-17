package com.main.nexus.dto;

import com.main.nexus.model.enums.NegativeReason;
import com.main.nexus.util.ReviewReasonMapper;

// Frequência de cada motivo negativo apontado em avaliações recebidas — usado no card
// "SoftSkills" do dashboard (tanto do profissional quanto da empresa).
public record SoftSkillFeedbackDTO(
        String reason,
        String enumValue,
        long count
) {

    public static SoftSkillFeedbackDTO of(NegativeReason reason, long count) {
        return new SoftSkillFeedbackDTO(ReviewReasonMapper.toPortuguese(reason), reason.name(), count);
    }
}
