package com.main.nexus.util;

import com.main.nexus.model.enums.NegativeReason;
import com.main.nexus.model.enums.PositiveReason;

// Fonte única de verdade pro nome legível em português de PositiveReason/NegativeReason —
// usado em qualquer lugar do código que precise exibir esses enums (cards de review,
// avaliações detalhadas, dashboards de analytics).
public class ReviewReasonMapper {

    private ReviewReasonMapper() {}

    public static String toPortuguese(PositiveReason reason) {
        return switch (reason) {
            case EXCELLENT_COMMUNICATION -> "Comunicação excelente";
            case HIGH_TECHNICAL_SKILL -> "Alta competência técnica";
            case DELIVERED_ON_TIME -> "Entregou no prazo";
            case TEAM_PLAYER -> "Trabalho em equipe";
            case PROACTIVE -> "Proatividade";
            case EXCEEDED_EXPECTATIONS -> "Superou expectativas";
            case RELIABLE -> "Confiável";
            case PUNCTUAL -> "Pontual";
            case HIGH_CODE_QUALITY -> "Alta qualidade de código";
            case GOOD_PROBLEM_SOLVING -> "Boa resolução de problemas";
        };
    }

    public static String toPortuguese(NegativeReason reason) {
        return switch (reason) {
            case MISSED_DEADLINES -> "Atrasos nas entregas";
            case POOR_COMMUNICATION -> "Comunicação deficiente";
            case LOW_CODE_QUALITY -> "Baixa qualidade técnica";
            case UNPROFESSIONAL -> "Falta de profissionalismo";
            case ABSENT -> "Ausências frequentes";
            case UNRELIABLE -> "Pouco confiável";
            case POOR_PROBLEM_SOLVING -> "Dificuldade em resolver problemas";
            case DID_NOT_MEET_EXPECTATIONS -> "Não atingiu as expectativas";
            case OTHER -> "Outros";
        };
    }
}
