package com.main.nexus.ratelimit;

import io.github.bucket4j.Bucket;

/**
 * Abstracao sobre onde os contadores de rate limit vivem. Existe uma unica impl
 * hoje ({@link CaffeineRateLimitStore}, em memoria). O objetivo e que trocar
 * para Redis no dia em que o backend rodar em varias instancias seja uma classe
 * nova implementando esta interface + um bean -- nao uma reescrita do filtro,
 * do {@link LoginAttemptService} ou do {@code ProjectAiExtractionService}.
 */
public interface RateLimitStore {

    /**
     * Balde da politica para aquela chave de cliente, criado sob demanda no
     * primeiro acesso. {@code key} ja vem namespaced pela politica (ver
     * {@link RateLimitFilter} / {@link LoginAttemptService}).
     */
    Bucket resolveBucket(String key, RateLimitPolicy policy);

    /**
     * Descarta o balde daquela chave. Usado pelo {@link LoginAttemptService}
     * quando um login da certo -- zera o contador de falhas consecutivas.
     */
    void evict(String key);
}
