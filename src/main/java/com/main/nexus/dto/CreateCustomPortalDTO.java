package com.main.nexus.dto;

import com.main.nexus.model.enums.CustomPortalPaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

// Corpo de POST /api/admin/custom-portals — criacao direta pelo Admin, sem
// solicitacao previa (contato comercial por fora). companyId aponta para um
// contratante ja cadastrado e APPROVED.
public record CreateCustomPortalDTO(
        Long companyId,
        String subdomain,
        String planName,
        BigDecimal planPrice,
        LocalDate subscriptionStartDate,
        LocalDate nextDueDate,
        CustomPortalPaymentStatus paymentStatus
) {}
