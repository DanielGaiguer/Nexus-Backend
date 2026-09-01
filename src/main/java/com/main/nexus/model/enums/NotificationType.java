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
    MATCH_STATUS_CHECK,      // match completa 30 dias em 15 dias, pede feedback de status à empresa
    MATCH_EXPIRED_REVIEW_REQUEST, // match completou 30 dias e expirou, pede avaliação a ambos os lados
    PROJECT_POSITIONS_FULL,  // projeto atingiu o limite de vagas e foi pausado automaticamente
    PROJECT_ADDED_TO_PORTFOLIO, // empresa confirmou o status do match como "trabalhando juntos" e o projeto foi adicionado ao portfólio do profissional
    ACCOUNT_MARKED_UNAVAILABLE, // profissional ficou 1 mês sem logar e foi marcado indisponível automaticamente
    PROPOSAL_RECEIVED,       // profissional enviou uma nova proposta para o projeto da empresa
    PROPOSAL_ACCEPTED,       // empresa aceitou a proposta do profissional
    PROPOSAL_REJECTED,       // empresa recusou ativamente a proposta do profissional
    PROPOSAL_POSITION_FILLED, // proposta foi auto-recusada porque outra proposta preencheu a última vaga
    PROPOSAL_EXPIRED,        // proposta expirou sem resposta da empresa dentro da validade
    SCREENING_INVITATION_RECEIVED, // profissional precisa responder uma etapa do processo seletivo
    SCREENING_SUBMITTED,           // profissional submeteu as respostas de uma etapa
    SCREENING_DECLINED,            // profissional recusou uma etapa obrigatória
    SCREENING_STAGE_APPROVED,      // empresa aprovou o avanço -- próxima etapa liberada
    SCREENING_STAGE_REPROVED,      // empresa reprovou nesta etapa -- processo encerrado
    SCREENING_EXPIRED,             // etapa expirou sem submissão dentro do prazo
    SCREENING_CANCELLED,           // etapa cancelada porque o match/projeto associado foi encerrado

    // ── Plataforma personalizada (CustomPortal) ────────────────────────
    CUSTOM_PORTAL_REQUEST_RECEIVED,  // contratante solicitou uma plataforma personalizada -> avisa o Admin
    CUSTOM_PORTAL_REQUEST_APPROVED,  // Admin aprovou a solicitação -> plataforma criada
    CUSTOM_PORTAL_REQUEST_REJECTED,  // Admin recusou a solicitação (com motivo)
    CUSTOM_PORTAL_RENEWAL_DUE,       // assinatura da plataforma personalizada perto do vencimento
    CUSTOM_PORTAL_SUSPENDED,         // plataforma personalizada suspensa (pelo Admin ou por inadimplência)
    PORTAL_SUBSCRIPTION_CHARGED,     // mensalidade da plataforma cobrada com sucesso
    PORTAL_SUBSCRIPTION_PAYMENT_FAILED, // falha ao cobrar a mensalidade -> carência antes da suspensão

    // ── Chat de suporte (Admin <-> usuário) ───────────────────────────
    SUPPORT_CONVERSATION_OPENED,     // conversa de suporte aberta (pelo Admin com o usuário, ou pelo usuário -> notifica os Admins)

    // ── Cobrança de comissão (Mercado Pago, Prompt 5) ────────────────
    COMMISSION_PAYMENT_CONFIRMED,    // a comissão de uma contratação foi cobrada com sucesso
    COMMISSION_PAYMENT_FAILED,       // a cobrança falhou / sem cartão -> contratante bloqueado

    // ── NFS-e por comissão (eNotas, Prompt 6) ───────────────────────
    NFSE_ISSUED,                     // a nota fiscal da comissão foi emitida
    NFSE_FAILED                      // a emissão falhou (ex.: dados fiscais incompletos)
}