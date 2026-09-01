package com.main.nexus.dto;

import java.math.BigDecimal;
import java.util.List;

// Extrato consolidado do contratante para o painel financeiro (Prompt 7).
// Os detalhes (linhas de cobranca e de nota fiscal) continuam vindo de
// /api/company/billing/charges e /invoices -- aqui ficam so os agregados e a
// lista das contratacoes aguardando a confirmacao de 30 dias.
public record ContractorFinanceOverviewDTO(
        boolean commissionEnabled,
        boolean simulated,
        // Total ja pago em comissao (cobrancas PAID).
        BigDecimal totalPaid,
        long paidCount,
        // Total em aberto (PENDING + PROCESSING + FAILED).
        BigDecimal totalPending,
        long pendingCount,
        boolean blocked,
        String blockMessage,
        // Contratacoes fechadas com a janela de 30 dias ainda aberta.
        long awaitingConfirmationCount,
        BigDecimal awaitingConfirmationEstimated,
        int freeHiresLimit,
        int usedFreeHires,
        int freeHiresRemaining,
        boolean commissionApplies,
        BigDecimal commissionPercentage,
        long invoicesIssuedCount,
        long invoicesPendingCount,
        List<AwaitingConfirmationDTO> awaitingConfirmations,
        // Mensalidades da plataforma personalizada (origem separada da comissao).
        boolean portalHasSubscription,
        BigDecimal portalTotalPaid,
        long portalPaidCount,
        BigDecimal portalTotalPending,
        long portalPendingCount
) {}
