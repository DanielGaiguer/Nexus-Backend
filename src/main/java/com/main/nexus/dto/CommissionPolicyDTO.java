package com.main.nexus.dto;

import com.main.nexus.model.CommissionPolicy;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Politica de comissao (singleton) para a tela do Admin. `updatedByAdminEmail`
// e so o e-mail do ultimo admin que alterou -- auditoria leve, pode vir nulo.
public record CommissionPolicyDTO(
        BigDecimal percentage,
        LocalDateTime updatedAt,
        String updatedByAdminEmail
) {
    public static CommissionPolicyDTO from(CommissionPolicy p) {
        return new CommissionPolicyDTO(
                p.getPercentage(),
                p.getUpdatedAt(),
                p.getUpdatedByAdmin() != null ? p.getUpdatedByAdmin().getEmail() : null
        );
    }
}
