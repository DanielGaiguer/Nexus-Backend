package com.main.nexus.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.main.nexus.dto.UserDTO;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Exercita o {@link RateLimitFilter} isoladamente (sem contexto Spring), com um
 * {@link CaffeineRateLimitStore} real -- e o Bucket4j de verdade contando.
 *
 * <p>As politicas de janela curta (PUBLIC_CONTENT 60/min, AUTHENTICATED 30/min,
 * PUBLIC_BEACON 120/min) repoem ~1 token/s: os testes consomem a capacidade e
 * entao aceitam que o 429 apareca "logo depois" (poucas tentativas), em vez de
 * fixar no N-esimo request exato -- senao ficariam fragil com o tempo que o
 * proprio teste leva. As de janela longa (EXPENSIVE 5/min, AUTH_* 10/h) nao
 * repoem nada no tempo do teste, entao ali a fronteira e checada no ponto exato.
 */
class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = newFilter(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private RateLimitFilter newFilter(boolean enabled) {
        return new RateLimitFilter(
                new CaffeineRateLimitStore(),
                new ClientIpResolver(),
                new RateLimitResponseWriter(),
                enabled);
    }

    private MockHttpServletResponse invoke(String method, String uri, String ip) throws Exception {
        return invoke(new MockHttpServletRequest(method, uri), ip);
    }

    private MockHttpServletResponse invoke(MockHttpServletRequest request, String ip) throws Exception {
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private void authenticate(long userId, String role) {
        UserDTO principal = new UserDTO(userId, userId + "@nexus.com", role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    /** Consome `count` requisicoes exigindo que TODAS passem (200). */
    private void drain(int count, String method, String uri, String ip) throws Exception {
        for (int i = 1; i <= count; i++) {
            assertEquals(200, invoke(method, uri, ip).getStatus(), "requisicao " + i + " deveria passar");
        }
    }

    /** Dispara ate `tries` requisicoes; retorna a primeira 429, ou a ultima resposta. */
    private MockHttpServletResponse blockedWithin(int tries, String method, String uri, String ip) throws Exception {
        MockHttpServletResponse last = null;
        for (int i = 0; i < tries; i++) {
            last = invoke(method, uri, ip);
            if (last.getStatus() == 429) {
                return last;
            }
        }
        return last;
    }

    // --- Publica: 60/min por IP ------------------------------------------------

    @Test
    void publicContent_blocksAfterCapacity_withCorrect429Shape() throws Exception {
        int cap = (int) RateLimitPolicy.PUBLIC_CONTENT.capacity();
        drain(cap, "GET", "/api/public/professionals", "1.1.1.1");

        MockHttpServletResponse blocked = blockedWithin(5, "GET", "/api/public/professionals", "1.1.1.1");
        assertEquals(429, blocked.getStatus());
        assertNotNull(blocked.getHeader("Retry-After"));
        assertTrue(Integer.parseInt(blocked.getHeader("Retry-After")) >= 1);
        String body = blocked.getContentAsString();
        assertTrue(body.contains("\"status\":429"), body);
        assertTrue(body.contains("\"error\":\"RATE_LIMIT_EXCEEDED\""), body);
        assertTrue(body.contains("\"retryAfter\""), body);
    }

    @Test
    void publicContent_isPerIp() throws Exception {
        int cap = (int) RateLimitPolicy.PUBLIC_CONTENT.capacity();
        drain(cap, "GET", "/api/public/professionals", "2.2.2.2");
        assertEquals(429, blockedWithin(5, "GET", "/api/public/professionals", "2.2.2.2").getStatus());
        // Outro IP: balde proprio, passa de primeira.
        assertEquals(200, invoke("GET", "/api/public/professionals", "3.3.3.3").getStatus());
    }

    @Test
    void beacon_hasItsOwnBucketSeparateFromContent() throws Exception {
        int cap = (int) RateLimitPolicy.PUBLIC_CONTENT.capacity();
        drain(cap, "GET", "/api/public/professionals", "4.4.4.4");
        assertEquals(429, blockedWithin(5, "GET", "/api/public/professionals", "4.4.4.4").getStatus());
        // O beacon (categoria publica, politica propria) segue livre.
        assertEquals(200,
                invoke("POST", "/api/public/custom-portal/acme/events", "4.4.4.4").getStatus());
    }

    // --- Autenticada: 30/min por userId -------------------------------------

    @Test
    void authenticated_blocksAfterCapacity_perUser() throws Exception {
        authenticate(42, "PROFESSIONAL");
        int cap = (int) RateLimitPolicy.AUTHENTICATED.capacity();
        drain(cap, "GET", "/api/proposals/mine", "9.9.9.9");
        assertEquals(429, blockedWithin(5, "GET", "/api/proposals/mine", "9.9.9.9").getStatus());

        // Outro usuario, mesmo IP: balde proprio.
        SecurityContextHolder.clearContext();
        authenticate(43, "COMPANY");
        assertEquals(200, invoke("GET", "/api/proposals/mine", "9.9.9.9").getStatus());
    }

    @Test
    void authenticated_coversMatchesAndMapAndRanking() throws Exception {
        authenticate(50, "COMPANY");
        int cap = (int) RateLimitPolicy.AUTHENTICATED.capacity();
        // Mesma politica -> mesmo balde para os tres, misturados.
        for (int i = 0; i < cap; i++) {
            String uri = switch (i % 3) {
                case 0 -> "/api/matches/1/history";
                case 1 -> "/api/map/professionals";
                default -> "/api/projects/1/ranking";
            };
            assertEquals(200, invoke("GET", uri, "10.0.0.1").getStatus());
        }
        assertEquals(429, blockedWithin(5, "GET", "/api/matches/1/history", "10.0.0.1").getStatus());
    }

    // --- Computacionalmente cara: 5/min por userId (janela longa: fronteira exata)

    @Test
    void expensive_allowsExactlyCapacityThenBlocks() throws Exception {
        authenticate(7, "COMPANY");
        int cap = (int) RateLimitPolicy.EXPENSIVE.capacity();
        drain(cap, "POST", "/api/comparison/candidates", "8.8.8.8");
        assertEquals(429, invoke("POST", "/api/comparison/candidates", "8.8.8.8").getStatus());
    }

    @Test
    void expensive_coversProjectWritesButNotClose() throws Exception {
        authenticate(7, "COMPANY");
        int cap = (int) RateLimitPolicy.EXPENSIVE.capacity();
        drain(cap, "PUT", "/api/projects/99", "8.8.8.8");
        assertEquals(429, invoke("PUT", "/api/projects/99", "8.8.8.8").getStatus());

        // /close nao dispara ranking -> nao e rate-limited.
        assertEquals(200, invoke("PUT", "/api/projects/99/close", "8.8.8.8").getStatus());
        // reopen/resume disparam -> mesmo balde EXPENSIVE (ja estourado).
        assertEquals(429, invoke("PUT", "/api/projects/99/reopen", "8.8.8.8").getStatus());
    }

    @Test
    void expensive_comparisonMineIsSameCategory() throws Exception {
        authenticate(7, "PROFESSIONAL");
        int cap = (int) RateLimitPolicy.EXPENSIVE.capacity();
        drain(cap, "POST", "/api/comparison/candidates/mine", "8.8.8.8");
        assertEquals(429, invoke("POST", "/api/comparison/candidates/mine", "8.8.8.8").getStatus());
    }

    // --- Autenticacao: contador proprio por endpoint (janela longa) -------

    @Test
    void auth_login_and_register_haveSeparateCounters() throws Exception {
        int cap = (int) RateLimitPolicy.AUTH_LOGIN.capacity();
        for (int i = 0; i < cap; i++) {
            assertEquals(200, invoke("POST", "/api/auth/login", "5.5.5.5").getStatus());
        }
        assertEquals(429, invoke("POST", "/api/auth/login", "5.5.5.5").getStatus());
        // register/professional tem contador proprio -> passa.
        assertEquals(200, invoke("POST", "/api/auth/register/professional", "5.5.5.5").getStatus());
        assertEquals(200, invoke("POST", "/api/auth/register/company", "5.5.5.5").getStatus());
    }

    // --- Fora de escopo ---------------------------------------------------

    @Test
    void unmatchedApiPath_isNeverLimited() throws Exception {
        for (int i = 0; i < 200; i++) {
            assertEquals(200, invoke("GET", "/api/company/profile", "6.6.6.6").getStatus());
        }
    }

    @Test
    void nonApiPath_isNeverLimited() throws Exception {
        for (int i = 0; i < 200; i++) {
            assertEquals(200, invoke("GET", "/actuator/health", "6.6.6.6").getStatus());
        }
    }

    @Test
    void disabledFilter_passesEverything() throws Exception {
        RateLimitFilter disabled = newFilter(false);
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/professionals");
            req.setRemoteAddr("7.7.7.7");
            MockHttpServletResponse res = new MockHttpServletResponse();
            disabled.doFilter(req, res, new MockFilterChain());
            assertEquals(200, res.getStatus());
        }
    }

    // --- Chave por IP respeita X-Forwarded-For --------------------------

    @Test
    void keyUsesForwardedForWhenPresent() throws Exception {
        int cap = (int) RateLimitPolicy.PUBLIC_CONTENT.capacity();
        for (int i = 0; i < cap; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/professionals");
            req.addHeader("X-Forwarded-For", "203.0.113.7, 127.0.0.1");
            invoke(req, "127.0.0.1");
        }
        boolean blocked = false;
        for (int i = 0; i < 5 && !blocked; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/professionals");
            req.addHeader("X-Forwarded-For", "203.0.113.7, 127.0.0.1");
            blocked = invoke(req, "127.0.0.1").getStatus() == 429;
        }
        assertTrue(blocked, "o cliente 203.0.113.7 deveria estar bloqueado");

        // Outro cliente real atras do mesmo BFF (remoteAddr identico) -> balde proprio.
        MockHttpServletRequest other = new MockHttpServletRequest("GET", "/api/public/professionals");
        other.addHeader("X-Forwarded-For", "203.0.113.99, 127.0.0.1");
        assertEquals(200, invoke(other, "127.0.0.1").getStatus());
    }

    // --- Sanidade: cadeia nao prossegue num 429 -----------------------

    @Test
    void blockedRequest_doesNotProceedDownstream() throws Exception {
        int cap = (int) RateLimitPolicy.EXPENSIVE.capacity();
        authenticate(1, "COMPANY");
        drain(cap, "POST", "/api/comparison/candidates", "1.2.3.4");

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/comparison/candidates");
        req.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);

        assertEquals(429, res.getStatus());
        assertNull(chain.getRequest(), "a cadeia nao deveria ter sido chamada num 429");
    }
}
