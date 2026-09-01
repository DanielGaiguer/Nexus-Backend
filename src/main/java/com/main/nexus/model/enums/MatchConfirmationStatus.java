package com.main.nexus.model.enums;

// Estado da janela de confirmacao pos-contratacao (30 dias). Ver MatchConfirmation
// e MatchStatusCheckService.
public enum MatchConfirmationStatus {
    // Janela aberta, aguardando a resposta de um ou dos dois lados.
    AWAITING_RESPONSES,
    // Os dois responderam que houve trabalho e os valores bateram (tolerancia
    // max(R$ 50, 2%)). confirmedAmount preenchido -> pronto para gerar comissao (Prompt 5).
    CONFIRMED,
    // Divergencia de valor, um lado diz que concluiu e o outro que nao, ou o
    // prazo estourou sem os dois responderem. Vai para revisao manual do Admin.
    PENDING_ADMIN_REVIEW,
    // Os dois responderam que NAO houve trabalho -> sem cobranca, nao conta nas
    // 3 contratacoes gratuitas, nao vai para o Admin.
    CLOSED_NO_CHARGE,
    // O Admin analisou um caso PENDING_ADMIN_REVIEW e nao conseguiu confirmar
    // (ex.: nenhum lado respondeu mesmo apos contato) -> encerrada SEM valor e
    // SEM comissao, explicitamente. Distinto de CLOSED_NO_CHARGE (Prompt 3).
    CLOSED_UNRESOLVED
}
