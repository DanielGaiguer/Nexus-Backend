package com.main.nexus.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import java.time.Duration;

/**
 * Cada politica de rate limit do projeto, com seu teto, sua janela e o tipo de
 * chave (identidade do cliente) usada para separar os baldes. Os numeros batem
 * 1:1 com a tabela aprovada no Passo 0 da feature.
 *
 * <p>Ficam como constantes aqui (e nao em application.properties) pelo mesmo
 * motivo do limite hardcoded que ja existia em
 * {@code ProjectAiExtractionService}: sao decisoes de produto, nao de ambiente.
 * O unico botao de ambiente e {@code nexus.rate-limit.enabled} (ver
 * {@link RateLimitFilter}), usado para desligar tudo em dev/local ou em teste.
 */
public enum RateLimitPolicy {

    /** Leituras publicas nao-autenticadas (diretorios, perfis, vagas, reviews). */
    PUBLIC_CONTENT(60, Duration.ofMinutes(1), KeyType.IP),

    /**
     * Beacon anonimo de analytics do portal white-label
     * ({@code POST /api/public/custom-portal/{subdomain}/events}). Balde proprio,
     * teto mais alto: um visitante normal dispara varios eventos por sessao
     * (scroll, page view) e nao pode se auto-bloquear de ver conteudo.
     */
    PUBLIC_BEACON(120, Duration.ofMinutes(1), KeyType.IP),

    /** Rotas autenticadas de uso corriqueiro (matches, propostas, feed, ranking, mapa). */
    AUTHENTICATED(30, Duration.ofMinutes(1), KeyType.USER),

    /**
     * Rotas que disparam o motor de matchmaking sobre toda a base de
     * profissionais ({@code generateRankingForProject}) ou o comparativo.
     * Chave = userId (hoje 1:1 com a empresa; ver decisao 9 do Passo 0).
     */
    EXPENSIVE(5, Duration.ofMinutes(1), KeyType.USER),

    /**
     * Autopreenchimento de vaga via IA ({@code POST /api/projects/ai-extract}).
     * Chamada paga ao Gemini -- teto por HORA, semantica de orcamento, nao de
     * pico. Aplicada dentro de {@code ProjectAiExtractionService}, nao no filtro.
     */
    AI_EXTRACT(10, Duration.ofHours(1), KeyType.USER),

    /** {@code POST /api/auth/login} -- tentativas por IP (contador proprio). */
    AUTH_LOGIN(10, Duration.ofHours(1), KeyType.IP),

    /** {@code POST /api/auth/register/professional} -- contador proprio por IP. */
    AUTH_REGISTER_PROFESSIONAL(10, Duration.ofHours(1), KeyType.IP),

    /** {@code POST /api/auth/register/company} -- contador proprio por IP. */
    AUTH_REGISTER_COMPANY(10, Duration.ofHours(1), KeyType.IP),

    /** {@code POST /api/auth/register/company/linkedin} -- contador proprio por IP. */
    AUTH_REGISTER_COMPANY_LINKEDIN(10, Duration.ofHours(1), KeyType.IP),

    /**
     * Bloqueio por falhas consecutivas de login. Chave = IP + e-mail tentado
     * (ver {@link LoginAttemptService} e a justificativa no pedido da feature).
     * Refill "intervally": os 5 tokens voltam todos de uma vez ao fim da janela,
     * entao a 5a falha bloqueia por 15 min inteiros -- nao goteia 1 token a cada
     * 3 min como seria com refill greedy.
     */
    LOGIN_FAILURE_BLOCK(5, Duration.ofMinutes(15), KeyType.IP_AND_EMAIL, RefillMode.INTERVALLY);

    public enum KeyType { IP, USER, IP_AND_EMAIL }

    private enum RefillMode { GREEDY, INTERVALLY }

    private final long capacity;
    private final Duration window;
    private final KeyType keyType;
    private final RefillMode refillMode;

    RateLimitPolicy(long capacity, Duration window, KeyType keyType) {
        this(capacity, window, keyType, RefillMode.GREEDY);
    }

    RateLimitPolicy(long capacity, Duration window, KeyType keyType, RefillMode refillMode) {
        this.capacity = capacity;
        this.window = window;
        this.keyType = keyType;
        this.refillMode = refillMode;
    }

    public KeyType keyType() {
        return keyType;
    }

    public long capacity() {
        return capacity;
    }

    public Duration window() {
        return window;
    }

    /** Configuracao do balde Bucket4j para esta politica (uma unica bandwidth). */
    public BucketConfiguration toBucketConfiguration() {
        Bandwidth bandwidth = refillMode == RefillMode.INTERVALLY
                ? Bandwidth.builder().capacity(capacity).refillIntervally(capacity, window).build()
                : Bandwidth.builder().capacity(capacity).refillGreedy(capacity, window).build();
        return BucketConfiguration.builder().addLimit(bandwidth).build();
    }
}
