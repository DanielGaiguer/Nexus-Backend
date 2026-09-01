package com.main.nexus.dto;

// Corpo de POST /api/company/billing/card -- so o token de cartao gerado pelo
// SDK do Mercado Pago no frontend. O backend NUNCA recebe numero/CVV/validade.
public record SaveCardRequestDTO(String cardToken) {}
