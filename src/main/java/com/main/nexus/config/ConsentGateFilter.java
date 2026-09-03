package com.main.nexus.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.service.UserConsentService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * "Gate de verdade" do re-aceite de Termos (LGPD). O gate primario e o layout
 * autenticado do frontend; este filtro fecha a lacuna de alguem contornar a tela
 * batendo direto na API.
 *
 * <p>Posicionado logo depois do {@code RateLimitFilter} na cadeia do Spring
 * Security (ver {@code SecurityConfig}) -- mesmo padrao: precisa do
 * {@code SecurityContext} ja populado pelo {@code JwtFilter}, e roda antes das
 * regras de autorizacao para nao ser mascarado.
 *
 * <p>So barra requisicoes <b>autenticadas</b> e <b>mutaveis</b>
 * ({@code POST/PUT/PATCH/DELETE}) sob {@code /api/**}. Leitura (GET) passa -- o
 * gate de UX do frontend cuida do lado da leitura, e barrar GET quebraria a
 * propria tela de re-aceite (que precisa carregar contexto). Exclui o mesmo
 * conjunto publico que {@code JwtFilter}/{@code RateLimitFilter} ja ignoram,
 * mais {@code /api/legal/**} (por onde o usuario retido se desbloqueia).
 *
 * <p>Ao barrar: {@code 428 Precondition Required} + corpo no formato padrao da
 * API ({@code {status, message, error}}), com {@code error=CONSENT_REQUIRED}.
 */
@Component
public class ConsentGateFilter extends OncePerRequestFilter {

    private static final List<String> SKIP_PREFIXES = List.of(
            "/api/auth/",
            "/api/legal/",
            "/api/public/",
            // Exclusão de conta (LGPD): não se pode condicionar o exercício do
            // direito de eliminação ao aceite de novos Termos.
            "/api/users/me",
            "/api/payments/mercadopago/webhook",
            "/api/invoices/enotas/webhook"
    );

    private final UserConsentService userConsentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConsentGateFilter(UserConsentService userConsentService) {
        this.userConsentService = userConsentService;
    }

    // Mesmo motivo do RateLimitFilter: nao re-rodar no ERROR dispatch para /error.
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (!appliesTo(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Long userId = currentUserId();
        if (userId == null) {
            // Sem principal: e um 401 que a cadeia de autorizacao resolve, nao
            // um problema de consentimento.
            filterChain.doFilter(request, response);
            return;
        }

        if (userConsentService.hasAcceptedActiveTerms(userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        writeConsentRequired(response);
    }

    private boolean appliesTo(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/")) {
            return false;
        }
        if (!isMutating(request.getMethod())) {
            return false;
        }
        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private boolean isMutating(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDTO user) {
            return user.id();
        }
        return null;
    }

    private void writeConsentRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.PRECONDITION_REQUIRED.value()); // 428
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 428);
        body.put("message", "É necessário aceitar a versão atualizada dos Termos de Uso para continuar.");
        body.put("error", "CONSENT_REQUIRED");

        objectMapper.writeValue(response.getWriter(), body);
    }
}
