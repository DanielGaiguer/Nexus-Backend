package com.main.nexus.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Estado da janela de confirmacao pos-contratacao, embutido no MatchResponseDTO
// para alimentar o indicador nos cards de match. `null` quando a janela ainda
// nao abriu para aquele match.
public record MatchConfirmationDTO(
        String status,                 // AWAITING_RESPONSES | CONFIRMED | PENDING_ADMIN_REVIEW | CLOSED_NO_CHARGE
        String pendingReason,          // VALUE_DIVERGENCE | NO_RESPONSE | COMPLETION_DISAGREEMENT | null
        LocalDateTime openedAt,
        LocalDateTime deadline,
        BigDecimal suggestedAmount,    // pre-preenchimento do formulario
        BigDecimal confirmedAmount,    // definido so quando CONFIRMED
        boolean companyAnswered,
        boolean professionalAnswered,
        Boolean viewerAnswered,        // se quem ve e parte do match; null para Admin
        boolean adminReviewed
) {}
