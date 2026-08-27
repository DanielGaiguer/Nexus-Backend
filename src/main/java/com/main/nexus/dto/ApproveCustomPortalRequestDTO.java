package com.main.nexus.dto;

import com.main.nexus.model.enums.CustomPortalPaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

// Corpo de POST /api/admin/custom-portal-requests/{id}/approve. O Admin define
// aqui o subdominio e os dados da assinatura ao aprovar a solicitacao.
// paymentStatus e opcional (default UP_TO_DATE no service).
public record ApproveCustomPortalRequestDTO(
        String subdomain,
        String planName,
        BigDecimal planPrice,
        LocalDate subscriptionStartDate,
        LocalDate nextDueDate,
        CustomPortalPaymentStatus paymentStatus
) {}
