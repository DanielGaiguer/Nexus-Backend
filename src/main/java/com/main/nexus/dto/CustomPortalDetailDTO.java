package com.main.nexus.dto;

import java.util.List;

// Detalhe de uma plataforma personalizada no painel Admin: os dados do portal
// + o historico de alteracoes de status para auditoria.
public record CustomPortalDetailDTO(
        CustomPortalDTO portal,
        CustomPortalRequestDTO originRequest,
        List<CustomPortalStatusHistoryDTO> statusHistory
) {}
