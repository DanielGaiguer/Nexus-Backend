package com.main.nexus.dto;

// GET /api/admin/invoices/mode -- diz ao painel do Admin se a simulação de
// emissão está disponível (modo simulate, sem eNotas).
public record NfseModeDTO(boolean live, boolean simulated) {}
