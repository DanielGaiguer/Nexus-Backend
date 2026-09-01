package com.main.nexus.dto;

// GET /api/admin/commission-charges/mode -- diz ao painel do Admin se a
// simulação de cobrança está disponível (modo simulate, sem Mercado Pago).
public record BillingModeDTO(boolean live, boolean simulated) {}
