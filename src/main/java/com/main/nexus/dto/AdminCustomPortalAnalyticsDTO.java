package com.main.nexus.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Agregado do dashboard geral do Admin — visão de todo o módulo de plataformas
 * personalizadas.
 *
 * {@code system} reaproveita o mesmo formato do dashboard do contratante
 * ({@link CustomPortalAnalyticsDTO}), só que somando todas as plataformas — assim
 * o front reusa o componente de análise que já existe. Os demais campos são as
 * métricas "mais profundas" que só fazem sentido no nível do sistema.
 */
public record AdminCustomPortalAnalyticsDTO(
        int rangeDays,
        // ── operacional ────────────────────────────────────────────────
        long totalPortals,
        long activePortals,
        long suspendedPortals,
        long canceledPortals,
        long pendingRequests,
        long overduePayments,
        long dueSoon,
        BigDecimal monthlyRecurringRevenue,
        // ── engajamento agregado (todas as plataformas) ───────────────
        CustomPortalAnalyticsDTO system,
        // ── recortes só do sistema ───────────────────────────────────
        List<StatusCount> portalsByStatus,
        List<PlanCount> portalsByPlan,
        List<PortalTraffic> topPortals,
        List<MonthlyCount> portalsCreatedPerMonth
) {
    /** Contagem de plataformas por status de ciclo de vida. */
    public record StatusCount(String status, long count) {}

    /** Contagem de plataformas por nome de plano. */
    public record PlanCount(String planName, long count) {}

    /** Tráfego de uma plataforma no período — alimenta o ranking. */
    public record PortalTraffic(
            Long portalId,
            String companyName,
            String subdomain,
            String status,
            long views,
            long applyClicks,
            double conversionRate) {}

    /** Plataformas criadas num mês (label "MM/AAAA"). */
    public record MonthlyCount(String label, long count) {}
}
