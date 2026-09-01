package com.main.nexus.dto;

import java.math.BigDecimal;
import java.util.List;

// Visao geral da receita de comissao para o painel financeiro do Admin
// (Prompt 7). As filas de trabalho (reconciliacao, NFS-e, cobrancas) continuam
// nas telas dos Prompts 3/5/6 -- aqui ficam os KPIs, o grafico e as contagens
// que linkam para elas.
public record AdminFinanceOverviewDTO(
        boolean commissionLive,
        boolean simulated,
        // Receita bruta de comissao ja arrecadada (cobrancas PAID).
        BigDecimal grossRevenue,
        long paidCount,
        // Comissao em aberto (PENDING + PROCESSING + FAILED).
        BigDecimal pendingRevenue,
        long pendingCount,
        long failedChargeCount,
        long blockedCompaniesCount,
        long pendingReconciliationCount,
        long pendingNfseCount,
        long issuedNfseCount,
        BigDecimal commissionPercentage,
        int freeHiresLimit,
        List<MonthlyAmountDTO> monthlyRevenue,
        // Mensalidades da plataforma personalizada (origem separada da comissao).
        BigDecimal portalGrossRevenue,
        long portalPaidCount,
        BigDecimal portalPendingRevenue,
        long portalPendingCount
) {}
