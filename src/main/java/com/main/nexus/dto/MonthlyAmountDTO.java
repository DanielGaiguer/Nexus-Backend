package com.main.nexus.dto;

import java.math.BigDecimal;

// Um ponto do grafico "receita de comissao por mes" do painel financeiro do
// Admin (Prompt 7). `label` no formato "ago/2026".
public record MonthlyAmountDTO(String label, BigDecimal value) {}
