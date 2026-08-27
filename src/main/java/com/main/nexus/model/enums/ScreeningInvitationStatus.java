package com.main.nexus.model.enums;

public enum ScreeningInvitationStatus {
    SENT,
    IN_PROGRESS,
    SUBMITTED,
    // Empresa aprovou o avanço -- se havia próxima etapa ativa, ela já foi criada; se era a
    // última, a ação pendente (interesse/aceite) foi retomada, ou (proposta) nada muda, ela
    // decide o aceite/recusa da proposta por conta própria.
    APPROVED,
    // Empresa reprovou nesta etapa -- definitivo, fecha o processo aqui (para
    // interesse/aceite, equivale a uma recusa formal do match; para proposta, não mexe na
    // proposta, só encerra o acompanhamento de etapas).
    REPROVED,
    // Profissional recusou a etapa obrigatória -- pode tentar de novo depois (a próxima
    // tentativa da mesma ação/gate abre uma nova ScreeningInvitation).
    DECLINED,
    // Prazo de resposta estourou sem submissão -- diferente de DECLINED, é definitivo: o gate
    // (ScreeningInvitationService.checkGate) não abre uma tentativa nova depois disso, mesma
    // lógica de plataformas de recrutamento reais (etapa vencida não pode ser refeita).
    EXPIRED,
    // O match ou o projeto associado foi encerrado enquanto o convite ainda estava pendente
    // (SENT/IN_PROGRESS) -- ver ScreeningInvitationService.cancelPendingForProfessionalProject/
    // cancelAllPendingForProject. Diferente de DECLINED (recusa ativa) e EXPIRED (prazo
    // estourado) -- aqui quem encerrou foi o relacionamento em volta, não a própria etapa.
    CANCELLED
}
