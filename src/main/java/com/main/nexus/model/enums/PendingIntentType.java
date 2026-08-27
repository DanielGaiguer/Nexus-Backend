package com.main.nexus.model.enums;

// A ação que ficou pendente de um ScreeningInvitation. Retomada automaticamente pelo
// ScreeningInvitationController.approveStage, e só quando não existe próxima etapa (ver
// ScreeningInvitation.pendingIntentType/pendingMatch/pendingProposal) -- PROPOSAL_SUBMIT nunca é
// retomado automaticamente (decisão confirmada com o usuário: aceite/recusa de proposta é sempre
// uma ação independente da empresa, feita a qualquer momento).
public enum PendingIntentType {
    // MatchService.professionalShowsInterest estava esperando -- retomado via
    // MatchService.applyProfessionalInterest(pendingMatch).
    MATCH_INTEREST,
    // MatchService.professionalAccepts estava esperando -- retomado via
    // MatchService.applyProfessionalAccept(pendingMatch).
    MATCH_ACCEPT,
    // ProposalService.submitProposal já criou a Proposal (sempre PENDING, nunca escondida) --
    // usado só como contexto informativo; nenhuma retomada automática acontece pra este caso.
    PROPOSAL_SUBMIT
}
