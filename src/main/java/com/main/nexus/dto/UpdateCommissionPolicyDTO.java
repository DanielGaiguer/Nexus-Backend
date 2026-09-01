package com.main.nexus.dto;

import java.math.BigDecimal;

// Corpo de PUT /api/admin/commission-policy -- o Admin ajusta o percentual unico
// (0 a 100). Validado em CommissionService.updatePolicy.
public record UpdateCommissionPolicyDTO(BigDecimal percentage) {}
