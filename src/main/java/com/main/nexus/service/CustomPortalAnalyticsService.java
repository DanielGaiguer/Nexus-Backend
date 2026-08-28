package com.main.nexus.service;

import com.main.nexus.dto.CustomPortalAnalyticsDTO;
import com.main.nexus.dto.CustomPortalAnalyticsDTO.DailyViews;
import com.main.nexus.dto.CustomPortalAnalyticsDTO.OpportunityViews;
import com.main.nexus.dto.CustomPortalAnalyticsDTO.ReferrerCount;
import com.main.nexus.dto.TrackPortalEventDTO;
import com.main.nexus.model.CustomPortal;
import com.main.nexus.model.CustomPortalVisitEvent;
import com.main.nexus.model.enums.CustomPortalEventType;
import com.main.nexus.model.enums.CustomPortalStatus;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.CustomPortalRepository;
import com.main.nexus.repository.CustomPortalVisitEventRepository;
import com.main.nexus.repository.ProjectRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * personalizada — alimenta o dashboard "Análises" do contratante.
 *
 * A gravação vem de um endpoint público (visitante anônimo), então tudo é
 * defensivo: entradas inválidas viram no-op silencioso (é um beacon, não faz
 * sentido devolver erro), strings truncadas, duração limitada.
 */
@Service
public class CustomPortalAnalyticsService {

    private static final int MAX_DURATION_SECONDS = 2 * 60 * 60; // 2h
    private static final int TOP_LIMIT = 6;

    @Autowired
    private CustomPortalVisitEventRepository eventRepository;

    @Autowired
    private CustomPortalRepository customPortalRepository;

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

    // ── Agregação (contratante) ─────────────────────────────────────

    public CustomPortalAnalyticsDTO getAnalyticsForUser(Long userId, int days) {
        int range = Math.min(Math.max(days, 1), 365);
        CustomPortal portal = portalByUser(userId);
        Long portalId = portal.getId();
        LocalDate startDate = LocalDate.now().minusDays(range - 1L);
        LocalDateTime since = startDate.atStartOfDay();

        long totalViews = eventRepository.countByType(
                portalId, CustomPortalEventType.PAGE_VIEW, since);
        long uniqueVisitors = eventRepository.countDistinctVisitors(
                portalId, CustomPortalEventType.PAGE_VIEW, since);
        long applyClicks = eventRepository.countByType(
                portalId, CustomPortalEventType.APPLY_CLICK, since);

        double conversionRate = totalViews > 0
                ? round1(applyClicks * 100.0 / totalViews) : 0.0;
        Double avg = eventRepository.avgSessionSeconds(portalId, since);
        double avgSessionSeconds = avg != null ? Math.round(avg) : 0.0;

        return new CustomPortalAnalyticsDTO(
                range,
                totalViews,
                uniqueVisitors,
                applyClicks,
                conversionRate,
                avgSessionSeconds,
                buildViewsPerDay(portalId, since, startDate, range),
                buildTopOpportunities(portalId, since),
                buildReferrers(portalId, since));
    }

    private List<DailyViews> buildViewsPerDay(
            Long portalId, LocalDateTime since, LocalDate startDate, int range) {
        Map<LocalDate, Long> byDay = new LinkedHashMap<>();
        for (Object[] row : eventRepository.viewsPerDay(portalId, since)) {
            byDay.put(toLocalDate(row[0]), ((Number) row[1]).longValue());
        }
        List<DailyViews> out = new ArrayList<>(range);
        for (int i = 0; i < range; i++) {
            LocalDate day = startDate.plusDays(i);
            out.add(new DailyViews(day, byDay.getOrDefault(day, 0L)));
        }
        return out;
    }

    private List<OpportunityViews> buildTopOpportunities(Long portalId, LocalDateTime since) {
        List<OpportunityViews> out = new ArrayList<>();
        for (Object[] row : eventRepository.topOpportunities(portalId, since)) {
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

    private List<ReferrerCount> buildReferrers(Long portalId, LocalDateTime since) {
        List<ReferrerCount> out = new ArrayList<>();
        for (Object[] row : eventRepository.referrerBreakdown(portalId, since)) {
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
