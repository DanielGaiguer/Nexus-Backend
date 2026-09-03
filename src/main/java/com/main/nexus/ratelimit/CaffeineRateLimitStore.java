package com.main.nexus.ratelimit;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Contadores de rate limit em memoria, apoiados no Caffeine via
 * {@code bucket4j-caffeine}. Instancia unica, local -- ver o comentario no
 * {@code pom.xml} sobre a troca por Redis se/quando escalar.
 *
 * <p>Caffeine da o que um {@code Map} puro (o jeito antigo em
 * {@code ProjectAiExtractionService}) nao dava: teto de entradas e expiracao das
 * chaves ociosas, sem o que o mapa de IPs/usuarios cresceria sem limite.
 */
@Component
public class CaffeineRateLimitStore implements RateLimitStore {

    // Teto duro de chaves distintas vivas ao mesmo tempo (IP + usuario + par
    // IP/e-mail). Bem acima do trafego real esperado; se estourar, o Caffeine
    // descarta as menos usadas -- no pior caso alguem que estava perto do limite
    // ganha um balde novo, nunca um bloqueio indevido.
    private static final long MAX_KEYS = 200_000;

    // Quanto tempo um balde ocioso (ja recarregado por completo) sobrevive antes
    // de ser descartado. >= a maior janela de politica (1h em AUTH_LOGIN) com
    // folga, para nao evict um balde que ainda esta "contando".
    private static final Duration KEEP_AFTER_REFILL = Duration.ofHours(2);

    private final CaffeineProxyManager<String> proxyManager;

    public CaffeineRateLimitStore() {
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder().maximumSize(MAX_KEYS);
        this.proxyManager = new CaffeineProxyManager<>(caffeine, KEEP_AFTER_REFILL);
    }

    @Override
    public Bucket resolveBucket(String key, RateLimitPolicy policy) {
        return proxyManager.builder().build(key, policy::toBucketConfiguration);
    }

    @Override
    public void evict(String key) {
        proxyManager.removeProxy(key);
    }
}
