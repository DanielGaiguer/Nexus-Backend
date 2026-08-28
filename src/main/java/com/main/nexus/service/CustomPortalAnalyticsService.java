package com.main.nexus.service;

import com.main.nexus.dto.AdminCustomPortalAnalyticsDTO;
import com.main.nexus.dto.AdminCustomPortalAnalyticsDTO.MonthlyCount;
import com.main.nexus.dto.AdminCustomPortalAnalyticsDTO.PlanCount;
import com.main.nexus.dto.AdminCustomPortalAnalyticsDTO.PortalTraffic;
import com.main.nexus.dto.AdminCustomPortalAnalyticsDTO.StatusCount;
import com.main.nexus.dto.CustomPortalAnalyticsDTO;
import com.main.nexus.dto.CustomPortalAnalyticsDTO.DailyViews;
import com.main.nexus.dto.CustomPortalAnalyticsDTO.OpportunityViews;
import com.main.nexus.dto.CustomPortalAnalyticsDTO.ReferrerCount;
import com.main.nexus.dto.TrackPortalEventDTO;
import com.main.nexus.model.CustomPortal;
import com.main.nexus.model.CustomPortalVisitEvent;
import com.main.nexus.model.enums.CustomPortalEventType;
import com.main.nexus.model.enums.CustomPortalPaymentStatus;
import com.main.nexus.model.enums.CustomPortalRequestStatus;
import com.main.nexus.model.enums.CustomPortalStatus;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.CustomPortalRepository;
import com.main.nexus.repository.CustomPortalRequestRepository;
import com.main.nexus.repository.CustomPortalVisitEventRepository;
import com.main.nexus.repository.ProjectRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Coleta e agregação das métricas de visita da página pública da plataforma
 * personalizada. Alimenta três dashboards:
 *
 * <ul>
 *   <li>o do contratante (a plataforma dele) — {@link #getAnalyticsForUser};</li>
 *   <li>o do Admin para uma plataforma específica — {@link #getAnalyticsForPortal};</li>
 *   <li>o do Admin para o sistema inteiro — {@link #getSystemAnalytics}.</li>
 * </ul>
 *
 * A gravação vem de um endpoint público (visitante anônimo), então tudo é
 * defensivo: entradas inválidas viram no-op silencioso (é um beacon, não faz
 * sentido devolver erro), strings truncadas, duração limitada.
 */
@Service
public class CustomPortalAnalyticsService {

    private static final int MAX_DURATION_SECONDS = 2 * 60 * 60; // 2h
    private static final int TOP_LIMIT = 6;
    private static final int TOP_PORTALS_LIMIT = 8;
    private static final int GROWTH_MONTHS = 6;
    private static final int RENEWAL_WINDOW_DAYS = 7;

    @Autowired
    private CustomPortalVisitEventRepository eventRepository;

    @Autowired
    private CustomPortalRepository customPortalRepository;

    @Autowired
    private CustomPortalRequestRepository customPortalRequestRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ProjectRepository projectRepository;

    // ── Coleta (público) ─────────────────────────────────────────────

    @Transactional
    public void recordEvent(String subdomain, TrackPortalEventDTO dto) {
        if (dto == null || dto.type() == null
                || dto.visitorId() == null || dto.visitorId().isBlank()) {
            return;
        }
        String normalized = subdomain == null ? "" : subdomain.trim().toLowerCase();
        CustomPortal portal = customPortalRepository.findBySubdomainIgnoreCase(normalized).orElse(null);
        if (portal == null || portal.getStatus() != CustomPortalStatus.ACTIVE) {
            return; // não registra tráfego de portal inexistente/inativo
        }

        CustomPortalVisitEvent event = new CustomPortalVisitEvent();
        event.setCustomPortal(portal);
        event.setVisitorId(trim(dto.visitorId(), 40));
        event.setType(dto.type());
        event.setPath(trim(dto.path(), 200));
        event.setOpportunityId(dto.opportunityId());
        if (dto.durationSeconds() != null) {
            event.setDurationSeconds(Math.max(0, Math.min(dto.durationSeconds(), MAX_DURATION_SECONDS)));
        }
        event.setReferrerHost(dto.referrerHost() == null ? null
                : trim(dto.referrerHost().toLowerCase(), 150));
        eventRepository.save(event);
    }

    // ── Agregação por plataforma ────────────────────────────────────

    /** Dashboard do contratante logado (a plataforma da empresa dele). */
    public CustomPortalAnalyticsDTO getAnalyticsForUser(Long userId, int days) {
        return buildAnalytics(portalByUser(userId).getId(), clampRange(days));
    }

    /** Dashboard do Admin para uma plataforma específica. */
    public CustomPortalAnalyticsDTO getAnalyticsForPortal(Long portalId, int days) {
        customPortalRepository.findById(portalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Custom portal not found: " + portalId));
        return buildAnalytics(portalId, clampRange(days));
    }

    private CustomPortalAnalyticsDTO buildAnalytics(Long portalId, int range) {
        LocalDate startDate = LocalDate.now().minusDays(range - 1L);
        LocalDateTime since = startDate.atStartOfDay();

        long totalViews = eventRepository.countByType(
                portalId, CustomPortalEventType.PAGE_VIEW, since);
        long uniqueVisitors = eventRepository.countDistinctVisitors(
                portalId, CustomPortalEventType.PAGE_VIEW, since);
        long applyClicks = eventRepository.countByType(
                portalId, CustomPortalEventType.APPLY_CLICK, since);
        Double avg = eventRepository.avgSessionSeconds(portalId, since);

        return new CustomPortalAnalyticsDTO(
                range,
                totalViews,
                uniqueVisitors,
                applyClicks,
                conversion(applyClicks, totalViews),
                avg != null ? Math.round(avg) : 0.0,
                buildViewsPerDay(eventRepository.viewsPerDay(portalId, since), startDate, range),
                buildTopOpportunities(eventRepository.topOpportunities(portalId, since)),
                buildReferrers(eventRepository.referrerBreakdown(portalId, since)));
    }

    // ── Agregação do sistema (Admin) ───────────────────────────────

    public AdminCustomPortalAnalyticsDTO getSystemAnalytics(int days) {
        int range = clampRange(days);
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(range - 1L);
        LocalDateTime since = startDate.atStartOfDay();

        List<CustomPortal> portals = customPortalRepository.findAll();

        long active = countStatus(portals, CustomPortalStatus.ACTIVE);
        long suspended = countStatus(portals, CustomPortalStatus.SUSPENDED);
        long canceled = countStatus(portals, CustomPortalStatus.CANCELED);

        long pendingRequests = customPortalRequestRepository.countByStatus(
                CustomPortalRequestStatus.PENDING);
        long overduePayments = portals.stream()
                .filter(p -> p.getPaymentStatus() == CustomPortalPaymentStatus.OVERDUE
                        && p.getStatus() != CustomPortalStatus.CANCELED)
                .count();
        LocalDate dueLimit = today.plusDays(RENEWAL_WINDOW_DAYS);
        long dueSoon = portals.stream()
                .filter(p -> p.getStatus() == CustomPortalStatus.ACTIVE
                        && p.getNextDueDate() != null
                        && !p.getNextDueDate().isBefore(today)
                        && !p.getNextDueDate().isAfter(dueLimit))
                .count();
        BigDecimal mrr = portals.stream()
                .filter(p -> p.getStatus() == CustomPortalStatus.ACTIVE)
                .map(p -> p.getPlanPrice() == null ? BigDecimal.ZERO : p.getPlanPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AdminCustomPortalAnalyticsDTO(
                range,
                portals.size(),
                active,
                suspended,
                canceled,
                pendingRequests,
                overduePayments,
                dueSoon,
                mrr,
                buildSystemEngagement(range, startDate, since),
                List.of(
                        new StatusCount("ACTIVE", active),
                        new StatusCount("SUSPENDED", suspended),
                        new StatusCount("CANCELED", canceled)),
                buildPlanBreakdown(portals),
                buildTopPortals(portals, since),
                buildGrowth(portals, today));
    }

    private CustomPortalAnalyticsDTO buildSystemEngagement(
            int range, LocalDate startDate, LocalDateTime since) {
        long totalViews = eventRepository.countByTypeAllPortals(
                CustomPortalEventType.PAGE_VIEW, since);
        long uniqueVisitors = eventRepository.countDistinctVisitorsAllPortals(
                CustomPortalEventType.PAGE_VIEW, since);
        long applyClicks = eventRepository.countByTypeAllPortals(
                CustomPortalEventType.APPLY_CLICK, since);
        Double avg = eventRepository.avgSessionSecondsAllPortals(since);

        return new CustomPortalAnalyticsDTO(
                range,
                totalViews,
                uniqueVisitors,
                applyClicks,
                conversion(applyClicks, totalViews),
                avg != null ? Math.round(avg) : 0.0,
                buildViewsPerDay(eventRepository.viewsPerDayAllPortals(since), startDate, range),
                buildTopOpportunities(eventRepository.topOpportunitiesAllPortals(since)),
                buildReferrers(eventRepository.referrerBreakdownAllPortals(since)));
    }

    private List<PlanCount> buildPlanBreakdown(List<CustomPortal> portals) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (CustomPortal p : portals) {
            String plan = p.getPlanName() == null || p.getPlanName().isBlank()
                    ? "—" : p.getPlanName().trim();
            counts.merge(plan, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new PlanCount(e.getKey(), e.getValue()))
                .toList();
    }

    private List<PortalTraffic> buildTopPortals(List<CustomPortal> portals, LocalDateTime since) {
        Map<Long, Long> viewsByPortal = toCountMap(eventRepository.viewsPerPortal(since));
        Map<Long, Long> clicksByPortal = toCountMap(eventRepository.applyClicksPerPortal(since));

        return portals.stream()
                .map(p -> {
                    long views = viewsByPortal.getOrDefault(p.getId(), 0L);
                    long clicks = clicksByPortal.getOrDefault(p.getId(), 0L);
                    String name = p.getCompany() != null && p.getCompany().getCompanyName() != null
                            ? p.getCompany().getCompanyName() : p.getSubdomain();
                    return new PortalTraffic(
                            p.getId(), name, p.getSubdomain(), p.getStatus().name(),
                            views, clicks, conversion(clicks, views));
                })
                .filter(t -> t.views() > 0)
                .sorted(Comparator.comparingLong(PortalTraffic::views).reversed())
                .limit(TOP_PORTALS_LIMIT)
                .toList();
    }

    private List<MonthlyCount> buildGrowth(List<CustomPortal> portals, LocalDate today) {
        Map<YearMonth, Long> byMonth = new HashMap<>();
        for (CustomPortal p : portals) {
            if (p.getCreatedAt() == null) continue;
            byMonth.merge(YearMonth.from(p.getCreatedAt()), 1L, Long::sum);
        }
        List<MonthlyCount> out = new ArrayList<>(GROWTH_MONTHS);
        YearMonth start = YearMonth.from(today).minusMonths(GROWTH_MONTHS - 1L);
        for (int i = 0; i < GROWTH_MONTHS; i++) {
            YearMonth ym = start.plusMonths(i);
            String label = String.format("%02d/%d", ym.getMonthValue(), ym.getYear());
            out.add(new MonthlyCount(label, byMonth.getOrDefault(ym, 0L)));
        }
        return out;
    }

    // ── Blocos compartilhados ──────────────────────────────────────

    private List<DailyViews> buildViewsPerDay(
            List<Object[]> rows, LocalDate startDate, int range) {
        Map<LocalDate, Long> byDay = new LinkedHashMap<>();
        for (Object[] row : rows) {
            byDay.put(toLocalDate(row[0]), ((Number) row[1]).longValue());
        }
        List<DailyViews> out = new ArrayList<>(range);
        for (int i = 0; i < range; i++) {
            LocalDate day = startDate.plusDays(i);
            out.add(new DailyViews(day, byDay.getOrDefault(day, 0L)));
        }
        return out;
    }

    private List<OpportunityViews> buildTopOpportunities(List<Object[]> rows) {
        List<OpportunityViews> out = new ArrayList<>();
        for (Object[] row : rows) {
            if (out.size() >= TOP_LIMIT) break;
            Long oppId = ((Number) row[0]).longValue();
            long views = ((Number) row[1]).longValue();
            String title = projectRepository.findById(oppId)
                    .map(p -> p.getTitle())
                    .orElse("Vaga #" + oppId);
            out.add(new OpportunityViews(oppId, title, views));
        }
        return out;
    }

    private List<ReferrerCount> buildReferrers(List<Object[]> rows) {
        List<ReferrerCount> out = new ArrayList<>();
        for (Object[] row : rows) {
            if (out.size() >= TOP_LIMIT) break;
            String host = (String) row[0];
            long count = ((Number) row[1]).longValue();
            out.add(new ReferrerCount(host == null || host.isBlank() ? "Direto" : host, count));
        }
        return out;
    }

    // ── Internos ────────────────────────────────────────────────────

    private CustomPortal portalByUser(Long userId) {
        Long companyId = companyRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Company profile not found"))
                .getId();
        return customPortalRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Custom portal not found for this company."));
    }

    private static long countStatus(List<CustomPortal> portals, CustomPortalStatus status) {
        return portals.stream().filter(p -> p.getStatus() == status).count();
    }

    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return map;
    }

    private static int clampRange(int days) {
        return Math.min(Math.max(days, 1), 365);
    }

    private static double conversion(long clicks, long views) {
        return views > 0 ? round1(clicks * 100.0 / views) : 0.0;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate d) return d;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof java.time.LocalDateTime dt) return dt.toLocalDate();
        return LocalDate.parse(value.toString().substring(0, 10));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
