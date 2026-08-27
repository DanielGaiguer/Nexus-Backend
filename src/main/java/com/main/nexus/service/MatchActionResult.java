package com.main.nexus.service;

import com.main.nexus.model.Match;

// Resultado de professionalShowsInterest/professionalAccepts -- ou a ação foi aplicada
// normalmente (screeningRequired=false, match já refletindo a transição), ou foi bloqueada pelo
// gate do questionário de triagem da vaga (screeningRequired=true, match ainda no estado
// anterior, screeningInvitationId aponta pra onde o profissional precisa responder antes de
// tentar de novo -- ver ScreeningInvitationService.checkGate).
public record MatchActionResult(Match match, boolean screeningRequired, Long screeningInvitationId) {
}
