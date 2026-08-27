package com.main.nexus.dto;

// Resposta de professional-accept/professional-interest -- quando screeningRequired=true, a ação
// (aceite/interesse) ainda não foi aplicada: o frontend deve redirecionar o profissional pra
// responder o questionário em screeningInvitationId antes de tentar de novo. A ação se completa
// sozinha assim que ele submete (ver ScreeningInvitationController.submit).
public record MatchActionResponseDTO(
        String message,
        boolean screeningRequired,
        Long screeningInvitationId
) {}
