package com.main.nexus.dto;

// GET /api/company/billing/config -- fonte unica para o frontend inicializar o
// SDK do Mercado Pago (evita NEXT_PUBLIC_*). `publicKey` e vazia quando o billing
// esta desligado.
public record BillingConfigDTO(
        boolean enabled,
        String publicKey,
        // true quando o billing está no modo simulado (sem Mercado Pago) -- o
        // frontend mostra "cartão de teste" em vez do formulário do MP.
        boolean simulated
) {}
