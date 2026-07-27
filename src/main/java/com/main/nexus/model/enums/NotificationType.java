package com.main.nexus.model.enums;

public enum NotificationType {
    NEW_INVITE,              // empresa demonstrou interesse no profissional
    MATCH_CONFIRMED,         // match foi confirmado por ambos os lados
    MATCH_CANCELLED,         // match confirmado foi cancelado por uma das partes
    INVITE_REJECTED,         // convite foi recusado
    NEW_REVIEW_RECEIVED,     // recebeu uma avaliação
    HIGH_SCORE_OPPORTUNITY,  // nova vaga com score >= 90%
    HIGH_SCORE_CANDIDATE,    // novo candidato com score >= 90% no ranking do projeto
    COMPANY_APPROVED,        // empresa foi aprovada pelo admin
    COMPANY_REJECTED,        // empresa foi rejeitada pelo admin
    PROJECT_CLOSED,          // um projeto foi encerrado
    NEW_INTEREST_RECEIVED,   // profissional demonstrou interesse na empresa
    COMPLETE_YOUR_PROFILE,
    NEW_COMPANY_REGISTRATION // empresa se cadastrou e aguarda aprovação do admin
}