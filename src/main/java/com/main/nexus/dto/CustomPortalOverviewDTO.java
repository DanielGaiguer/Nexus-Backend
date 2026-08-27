package com.main.nexus.dto;

// O que a tela "Plataforma personalizada" do contratante precisa numa unica ida
// ao servidor: a ultima solicitacao dele (se houver), o portal dele (se ja
// existir) e se ele pode abrir uma nova solicitacao agora.
public record CustomPortalOverviewDTO(
        CustomPortalRequestDTO latestRequest,
        CustomPortalDTO portal,
        boolean canRequest
) {}
