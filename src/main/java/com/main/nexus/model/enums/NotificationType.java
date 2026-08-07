package com.main.nexus.model.enums;

public enum NotificationType {
    NEW_INVITE,              // empresa demonstrou interesse no profissional
    MATCH_CONFIRMED,         // match foi confirmado por ambos os lados
    MATCH_CANCELLED,         // match confirmado foi cancelado por uma das partes
    INVITE_REJECTED,         // convite foi recusado
    HIGH_SCORE_OPPORTUNITY,  // nova vaga com score >= 90%
    HIGH_SCORE_CANDIDATE,    // novo candidato com score >= 90% no ranking do projeto
    COMPANY_APPROVED,        // empresa foi aprovada pelo admin
    COMPANY_REJECTED,        // empresa foi rejeitada pelo admin
    PROJECT_CLOSED,          // um projeto foi encerrado
    PROJECT_CLOSED_BY_ADMIN, // um projeto foi encerrado por um administrador (notifica a empresa dona)
    NEW_INTEREST_RECEIVED,   // profissional demonstrou interesse na empresa
    COMPLETE_YOUR_PROFILE,   // caso o perfil nao foi completdo
    NEW_COMPANY_REGISTRATION, // empresa se cadastrou e aguarda aprovação do admin
    MATCH_STATUS_CHECK,      // match está próximo dos 30 dias, pede feedback de status à empresa
    MATCH_EXPIRED_REVIEW_REQUEST, // match completou 30 dias e expirou, pede avaliação a ambos os lados
    PROJECT_POSITIONS_FULL   // projeto atingiu o limite de vagas e foi pausado automaticamente
}