package com.main.nexus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cliente REST fino do Mercado Pago (camada financeira, Prompt 5). Mesmo desenho
 * de GitHubService/SupabaseStorageService: RestTemplate cru + ObjectMapper,
 * credenciais por @Value, ResponseStatusException(502) no erro.
 *
 * A tokenizacao do cartao acontece INTEIRAMENTE no frontend (SDK do MP). Aqui
 * so trafegam tokens opacos: customer_id, card_id e card_token de uso unico.
 */
@Service
public class MercadoPagoClient {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoClient.class);

    @Value("${mercadopago.base-url}")
    private String baseUrl;

    @Value("${mercadopago.access-token}")
    private String accessToken;

    @Value("${mercadopago.public-key}")
    private String publicKey;

    @Value("${mercadopago.notification-url}")
    private String notificationUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean hasCredentials() {
        return accessToken != null && !accessToken.isBlank();
    }

    public String publicKey() {
        return publicKey == null ? "" : publicKey;
    }

    // ── Customer + cartao ───────────────────────────────────────────

    /** Cria (ou reaproveita) o customer do MP para um e-mail e retorna o id. */
    public String getOrCreateCustomer(String email, String firstName) {
        // MP exige e-mail unico por customer -- se ja existe, busca.
        String existing = findCustomerByEmail(email);
        if (existing != null) {
            return existing;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        if (firstName != null && !firstName.isBlank()) {
            body.put("first_name", firstName);
        }
        JsonNode res = post("/v1/customers", body, null);
        return requireText(res, "id", "customer");
    }

    private String findCustomerByEmail(String email) {
        try {
            JsonNode res = get("/v1/customers/search?email="
                    + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8));
            JsonNode results = res.path("results");
            if (results.isArray() && !results.isEmpty()) {
                return results.get(0).path("id").asText(null);
            }
        } catch (Exception e) {
            log.warn("MP customer search failed for {}: {}", email, e.getMessage());
        }
        return null;
    }

    /** Salva o cartao (a partir do token do frontend) e retorna o resumo. */
    public SavedCard saveCard(String customerId, String cardToken) {
        JsonNode res = post("/v1/customers/" + customerId + "/cards",
                Map.of("token", cardToken), null);
        return new SavedCard(
                requireText(res, "id", "card"),
                res.path("last_four_digits").asText(null),
                res.path("payment_method").path("id").asText(null),
                res.path("payment_method").path("name").asText(null),
                res.path("expiration_month").isMissingNode() ? null
                        : res.path("expiration_month").asInt(),
                res.path("expiration_year").isMissingNode() ? null
                        : res.path("expiration_year").asInt(),
                res.path("cardholder").path("name").asText(null));
    }

    public void deleteCard(String customerId, String cardId) {
        try {
            exchange(HttpMethod.DELETE, "/v1/customers/" + customerId + "/cards/" + cardId, null, null);
        } catch (Exception e) {
            // Cartao ja removido / customer inexistente: nao e fatal para o nosso fluxo.
            log.warn("MP deleteCard failed ({}/{}): {}", customerId, cardId, e.getMessage());
        }
    }

    // ── Cobranca ───────────────────────────────────────────────────

    /**
     * Gera um card token de uso unico a partir de um cartao ja salvo -- necessario
     * para cada cobranca. Sem CVV (cartao salvo em customer); alguns emissores
     * ainda recusam, o que cai no fluxo de "cartao recusado".
     */
    public String createCardTokenFromSavedCard(String cardId) {
        JsonNode res = post("/v1/card_tokens", Map.of("card_id", cardId), null);
        return requireText(res, "id", "card_token");
    }

    public Payment createPayment(BigDecimal amount, String cardTokenId, String customerId,
                                 String description, String externalReference, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transaction_amount", amount);
        body.put("token", cardTokenId);
        body.put("description", description);
        body.put("installments", 1);
        body.put("external_reference", externalReference);
        Map<String, Object> payer = new LinkedHashMap<>();
        payer.put("type", "customer");
        payer.put("id", customerId);
        body.put("payer", payer);
        if (notificationUrl != null && !notificationUrl.isBlank()) {
            body.put("notification_url", notificationUrl);
        }
        JsonNode res = post("/v1/payments", body, idempotencyKey);
        return toPayment(res);
    }

    public Payment getPayment(String paymentId) {
        return toPayment(get("/v1/payments/" + paymentId));
    }

    private Payment toPayment(JsonNode res) {
        return new Payment(
                res.path("id").asText(null),
                res.path("status").asText(null),
                res.path("status_detail").asText(null),
                res.path("external_reference").asText(null));
    }

    // ── Assinatura recorrente (/preapproval) ───────────────────────
    // Usada pela cobranca da plataforma personalizada. O MP cobra o cartao no
    // ciclo definido e envia webhooks (type=subscription_authorized_payment).

    /**
     * Cria a assinatura recorrente. `cardTokenId` vem do frontend (tokenizacao no
     * SDK); com ele + status "authorized" o MP ja comeca a cobrar em `startDate`.
     */
    public Preapproval createPreapproval(BigDecimal monthlyAmount, String cardTokenId,
                                         String payerEmail, java.time.LocalDate startDate,
                                         String reason, String externalReference) {
        Map<String, Object> autoRecurring = new LinkedHashMap<>();
        autoRecurring.put("frequency", 1);
        autoRecurring.put("frequency_type", "months");
        autoRecurring.put("transaction_amount", monthlyAmount);
        autoRecurring.put("currency_id", "BRL");
        if (startDate != null) {
            // ISO 8601 com offset -- o MP começa a cobrar nesta data.
            autoRecurring.put("start_date", startDate + "T00:00:00.000-03:00");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason);
        body.put("external_reference", externalReference);
        body.put("payer_email", payerEmail);
        body.put("auto_recurring", autoRecurring);
        body.put("card_token_id", cardTokenId);
        body.put("status", "authorized");
        if (notificationUrl != null && !notificationUrl.isBlank()) {
            body.put("notification_url", notificationUrl);
        }
        // back_url e obrigatorio na API; sem checkout hospedado, aponta de volta pro app.
        body.put("back_url", notificationUrl != null && !notificationUrl.isBlank()
                ? notificationUrl : "https://nexus.local/company/custom-portal");
        return toPreapproval(post("/preapproval", body, null));
    }

    /** Troca o cartao da assinatura (novo token do frontend). */
    public Preapproval updatePreapprovalCard(String preapprovalId, String cardTokenId) {
        return toPreapproval(put("/preapproval/" + preapprovalId,
                Map.of("card_token_id", cardTokenId)));
    }

    /** Ajusta o valor mensal da assinatura (vale a partir do proximo ciclo). */
    public Preapproval updatePreapprovalAmount(String preapprovalId, BigDecimal monthlyAmount) {
        Map<String, Object> autoRecurring = new LinkedHashMap<>();
        autoRecurring.put("transaction_amount", monthlyAmount);
        autoRecurring.put("currency_id", "BRL");
        return toPreapproval(put("/preapproval/" + preapprovalId,
                Map.of("auto_recurring", autoRecurring)));
    }

    /** status: "authorized" (retomar) | "paused" (suspender) | "cancelled" (encerrar). */
    public Preapproval updatePreapprovalStatus(String preapprovalId, String status) {
        return toPreapproval(put("/preapproval/" + preapprovalId, Map.of("status", status)));
    }

    public Preapproval getPreapproval(String preapprovalId) {
        return toPreapproval(get("/preapproval/" + preapprovalId));
    }

    /** Detalhe de um pagamento recorrente gerado pela assinatura. */
    public AuthorizedPayment getAuthorizedPayment(String authorizedPaymentId) {
        JsonNode res = get("/authorized_payments/" + authorizedPaymentId);
        JsonNode payment = res.path("payment");
        return new AuthorizedPayment(
                res.path("id").asText(null),
                res.path("preapproval_id").asText(null),
                payment.path("id").asText(null),
                payment.path("status").asText(null),
                payment.path("status_detail").asText(null));
    }

    /** Resumo do cartao a partir do token de uso unico (so exibicao). */
    public CardTokenInfo getCardToken(String cardTokenId) {
        try {
            JsonNode res = get("/v1/card_tokens/" + cardTokenId);
            return new CardTokenInfo(
                    res.path("last_four_digits").asText(null),
                    res.path("payment_method").path("name").asText(
                            res.path("payment_method").path("id").asText(null)));
        } catch (Exception e) {
            log.warn("MP getCardToken failed: {}", e.getMessage());
            return new CardTokenInfo(null, null);
        }
    }

    private Preapproval toPreapproval(JsonNode res) {
        return new Preapproval(
                requireText(res, "id", "preapproval"),
                res.path("status").asText(null),
                res.path("next_payment_date").asText(null));
    }

    // ── HTTP ───────────────────────────────────────────────────────

    private JsonNode get(String path) {
        return exchange(HttpMethod.GET, path, null, null);
    }

    private JsonNode post(String path, Object body, String idempotencyKey) {
        return exchange(HttpMethod.POST, path, body, idempotencyKey);
    }

    private JsonNode put(String path, Object body) {
        return exchange(HttpMethod.PUT, path, body, null);
    }

    private JsonNode exchange(HttpMethod method, String path, Object body, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (idempotencyKey != null) {
            headers.set("X-Idempotency-Key", idempotencyKey);
        }
        HttpEntity<Object> request = new HttpEntity<>(body, headers);
        try {
            String response = restTemplate.exchange(baseUrl + path, method, request, String.class).getBody();
            return objectMapper.readTree(response == null ? "{}" : response);
        } catch (HttpClientErrorException e) {
            // 4xx do MP -- devolve o corpo para o BillingService classificar (cartao
            // recusado vs. dado invalido). 502 com a mensagem do MP anexada.
            String mpBody = e.getResponseBodyAsString();
            log.warn("MP {} {} -> {} {}", method, path, e.getStatusCode(), mpBody);
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "Mercado Pago rejeitou a requisicao: " + shorten(mpBody), e);
        } catch (Exception e) {
            log.error("MP {} {} failed: {}", method, path, e.getMessage());
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "Falha de comunicacao com o Mercado Pago.", e);
        }
    }

    private String requireText(JsonNode node, String field, String what) {
        String v = node.path(field).asText(null);
        if (v == null || v.isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "Mercado Pago nao retornou '" + field + "' ao criar " + what + ".");
        }
        return v;
    }

    private String shorten(String s) {
        if (s == null) return "";
        return s.length() > 400 ? s.substring(0, 400) + "..." : s;
    }

    // ── Tipos de retorno ──────────────────────────────────────────

    public record SavedCard(
            String id,
            String lastFourDigits,
            String paymentMethodId,
            String paymentMethodName,
            Integer expirationMonth,
            Integer expirationYear,
            String cardholderName) {}

    public record Payment(
            String id,
            String status,
            String statusDetail,
            String externalReference) {}

    public record Preapproval(
            String id,
            String status,
            String nextPaymentDate) {}

    public record AuthorizedPayment(
            String id,
            String preapprovalId,
            String paymentId,
            String paymentStatus,
            String paymentStatusDetail) {}

    public record CardTokenInfo(
            String lastFourDigits,
            String paymentMethodName) {}
}
