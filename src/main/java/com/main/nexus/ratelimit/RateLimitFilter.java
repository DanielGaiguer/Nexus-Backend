package com.main.nexus.ratelimit;

import com.main.nexus.dto.UserDTO;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limiting por politica (Token Bucket / Bucket4j). Posicionado na cadeia do
 * Spring Security logo depois do {@code JwtFilter} (ver {@code SecurityConfig}),
 * entao o {@code SecurityContext} ja esta populado para as politicas com chave
 * por usuario.
 *
 * <p>Ao estourar o limite: 429 + header {@code Retry-After} (segundos ate o
 * proximo token) + corpo no formato padrao da API com o campo {@code error}
 * ({@code RATE_LIMIT_EXCEEDED}) para o frontend distinguir de um bloqueio de
 * login.
 *
 * <p>O bloqueio por falhas de login e a cota de IA nao passam por aqui: o
 * primeiro precisa do e-mail tentado e do resultado da validacao de credenciais
 * ({@code AuthService.login} -> {@link LoginAttemptService}); o segundo tem
 * semantica de orcamento e mora em {@code ProjectAiExtractionService}.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /** Um casamento (metodo + padrao de path) -> politica. Primeiro que casa vence. */
    private record Rule(String method, String pattern, RateLimitPolicy policy) {
        boolean matches(String requestMethod, String path, AntPathMatcher matcher) {
            return (method == null || method.equals(requestMethod)) && matcher.match(pattern, path);
        }
    }

    // Ordem importa: do mais especifico para o mais generico.
    private static final List<Rule> RULES = List.of(
            // --- Autenticacao (cada endpoint com seu proprio contador, por IP) ---
            new Rule("POST", "/api/auth/login", RateLimitPolicy.AUTH_LOGIN),
            new Rule("POST", "/api/auth/register/company/linkedin", RateLimitPolicy.AUTH_REGISTER_COMPANY_LINKEDIN),
            new Rule("POST", "/api/auth/register/company", RateLimitPolicy.AUTH_REGISTER_COMPANY),
            new Rule("POST", "/api/auth/register/professional", RateLimitPolicy.AUTH_REGISTER_PROFESSIONAL),

            // --- Computacionalmente cara (dispara o matchmaking / comparativo) ---
            new Rule("POST", "/api/comparison/candidates/mine", RateLimitPolicy.EXPENSIVE),
            new Rule("POST", "/api/comparison/candidates", RateLimitPolicy.EXPENSIVE),
            new Rule("POST", "/api/projects", RateLimitPolicy.EXPENSIVE),
            new Rule("PUT", "/api/projects/*", RateLimitPolicy.EXPENSIVE),
            new Rule("PUT", "/api/projects/*/reopen", RateLimitPolicy.EXPENSIVE),
            new Rule("PUT", "/api/projects/*/resume", RateLimitPolicy.EXPENSIVE),

            // --- Autenticada (uso corriqueiro) ---
            new Rule("GET", "/api/projects/*/ranking", RateLimitPolicy.AUTHENTICATED),
            new Rule("GET", "/api/professional/opportunities", RateLimitPolicy.AUTHENTICATED),
            new Rule(null, "/api/map/**", RateLimitPolicy.AUTHENTICATED),
            new Rule(null, "/api/matches/**", RateLimitPolicy.AUTHENTICATED),
            new Rule(null, "/api/proposals/**", RateLimitPolicy.AUTHENTICATED),

            // --- Publica ---
            new Rule("POST", "/api/public/custom-portal/*/events", RateLimitPolicy.PUBLIC_BEACON),
            new Rule(null, "/api/public/**", RateLimitPolicy.PUBLIC_CONTENT),
            new Rule("GET", "/api/reputation/**", RateLimitPolicy.PUBLIC_CONTENT),
            new Rule("GET", "/api/skills", RateLimitPolicy.PUBLIC_CONTENT),
            new Rule("GET", "/api/skills/categories", RateLimitPolicy.PUBLIC_CONTENT),
            new Rule("GET", "/api/reviews/*/*/count", RateLimitPolicy.PUBLIC_CONTENT),
            new Rule("GET", "/api/reviews/*/*/top3", RateLimitPolicy.PUBLIC_CONTENT),
            new Rule("GET", "/api/reviews/*/*/all", RateLimitPolicy.PUBLIC_CONTENT)
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final RateLimitStore store;
    private final ClientIpResolver clientIpResolver;
    private final RateLimitResponseWriter responseWriter;
    private final boolean enabled;

    public RateLimitFilter(RateLimitStore store,
                           ClientIpResolver clientIpResolver,
                           RateLimitResponseWriter responseWriter,
                           @Value("${nexus.rate-limit.enabled:true}") boolean enabled) {
        this.store = store;
        this.clientIpResolver = clientIpResolver;
        this.responseWriter = responseWriter;
        this.enabled = enabled;
    }

    // So conta no dispatch inicial da requisicao. Sem isto, uma resposta de erro
    // que o container despacha para /error faz o OncePerRequestFilter re-rodar o
    // doFilterInternal no ERROR dispatch -- e a mesma requisicao logica
    // consumiria 2 tokens (ex.: um GET /api/matches/{id} que da 404).
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!enabled || !path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitPolicy policy = matchPolicy(request.getMethod(), path);
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = resolveKey(policy, request);
        Bucket bucket = store.resolveBucket(key, policy);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfter = Math.max(1, (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0));
        responseWriter.writeTooManyRequests(
                response,
                retryAfter,
                RateLimitExceededException.REQUESTS,
                "Voce fez muitas requisicoes. Tente novamente em " + retryAfter + " segundos.");
    }

    private RateLimitPolicy matchPolicy(String method, String path) {
        for (Rule rule : RULES) {
            if (rule.matches(method, path, pathMatcher)) {
                return rule.policy();
            }
        }
        return null;
    }

    private String resolveKey(RateLimitPolicy policy, HttpServletRequest request) {
        String prefix = policy.name() + ":";
        if (policy.keyType() == RateLimitPolicy.KeyType.USER) {
            Long userId = currentUserId();
            if (userId != null) {
                return prefix + "user:" + userId;
            }
            // Politica de usuario sem principal: nao deveria acontecer (essas rotas
            // exigem login no SecurityConfig e este filtro roda depois do JwtFilter).
            // Cai para o IP so para nao deixar um caminho sem protecao nenhuma.
            return prefix + "anon-ip:" + clientIpResolver.resolve(request);
        }
        return prefix + clientIpResolver.resolve(request);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDTO user) {
            return user.id();
        }
        return null;
    }
}
