package com.main.nexus.service;

import com.main.nexus.model.ReputationMetrics;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Memo de {@link ReputationMetrics} com escopo de request.
 *
 * As listas de match/proposta/candidato reconstroem o score de cada linha
 * (MatchService.getScore / getScoreBreakdown), e cada reconstrução relê o
 * ReputationMetrics do mesmo profissional/empresa 3-4x. Numa lista de N linhas
 * isso virava ~3N SELECTs em tb_reputation_metrics -- medido: 240 de 350 queries
 * num único GET /api/professional/matches (53 matches). O memo colapsa isso para
 * 1 SELECT por profissional/empresa distinto por request.
 *
 * Escopo de request => o mapa nasce vazio a cada request e morre no fim dele,
 * então nunca serve valor velho entre requests. {@link ReputationService} acessa
 * via helpers que ignoram o memo quando não há request ativo (ex.: jobs
 * @Scheduled), preservando exatamente o comportamento atual nesses casos.
 */
@Component
@RequestScope
public class RequestReputationCache {

    private final Map<String, ReputationMetrics> byKey = new HashMap<>();

    ReputationMetrics get(String key) {
        return byKey.get(key);
    }

    void put(String key, ReputationMetrics metrics) {
        byKey.put(key, metrics);
    }

    void evict(String key) {
        byKey.remove(key);
    }
}
