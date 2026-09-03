package com.main.nexus.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.EstimationProbe;
import org.springframework.stereotype.Service;

/**
 * Bloqueio temporario de login por falhas consecutivas. Chave = IP + e-mail
 * tentado -- so por e-mail deixaria qualquer um bloquear a conta alheia de
 * proposito; so por IP nao protegeria uma conta atacada de varios IPs.
 *
 * <p>Modelado como um balde de 5 tokens que recarrega os 5 de uma vez a cada 15
 * min ({@link RateLimitPolicy#LOGIN_FAILURE_BLOCK}, refill intervally):
 * <ul>
 *   <li>cada falha de credencial consome 1 token;</li>
 *   <li>na 6a tentativa (0 tokens) o login e barrado por ate 15 min;</li>
 *   <li>um login bem-sucedido descarta o balde -- zera as falhas.</li>
 * </ul>
 *
 * <p>Vive no mesmo {@link RateLimitStore} do resto do rate limit, entao migrar
 * para Redis e a mesma troca pontual -- sem tabela no banco para isso.
 */
@Service
public class LoginAttemptService {

    private final RateLimitStore store;

    public LoginAttemptService(RateLimitStore store) {
        this.store = store;
    }

    /**
     * Barra o login se o par IP/e-mail estiver no periodo de bloqueio. Chamado
     * no comeco de {@code AuthService.login}, antes de conferir a senha.
     */
    public void assertNotBlocked(String clientIp, String attemptedEmail) {
        Bucket bucket = bucketFor(clientIp, attemptedEmail);
        EstimationProbe probe = bucket.estimateAbilityToConsume(1);
        if (!probe.canBeConsumed()) {
            long retryAfter = secondsUntil(probe.getNanosToWaitForRefill());
            throw new RateLimitExceededException(
                    retryAfter,
                    RateLimitExceededException.LOGIN_BLOCKED,
                    "Conta temporariamente bloqueada por tentativas de login malsucedidas. "
                    + "Tente novamente em " + retryAfter + " segundos.");
        }
    }

    /** Registra uma falha de credencial (e-mail/senha incorretos). */
    public void recordFailure(String clientIp, String attemptedEmail) {
        bucketFor(clientIp, attemptedEmail).tryConsume(1);
    }

    /** Login bem-sucedido: zera o contador de falhas consecutivas do par. */
    public void recordSuccess(String clientIp, String attemptedEmail) {
        store.evict(key(clientIp, attemptedEmail));
    }

    private Bucket bucketFor(String clientIp, String attemptedEmail) {
        return store.resolveBucket(key(clientIp, attemptedEmail), RateLimitPolicy.LOGIN_FAILURE_BLOCK);
    }

    private String key(String clientIp, String attemptedEmail) {
        String email = attemptedEmail == null ? "" : attemptedEmail.trim().toLowerCase();
        return RateLimitPolicy.LOGIN_FAILURE_BLOCK.name() + ":" + clientIp + ":" + email;
    }

    private long secondsUntil(long nanos) {
        return Math.max(1, (long) Math.ceil(nanos / 1_000_000_000.0));
    }
}
