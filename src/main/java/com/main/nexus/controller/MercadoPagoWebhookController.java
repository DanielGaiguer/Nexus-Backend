package com.main.nexus.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.nexus.service.BillingService;
import com.main.nexus.service.PortalSubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Webhook do Mercado Pago (Prompt 5). Público (permitAll em SecurityConfig).
// O MP chama com ?type=payment&data.id=... e/ou um corpo JSON {type, data:{id}}.
// Sempre responde 200 (o MP re-tenta em não-2xx) e processa de forma tolerante.
@RestController
@RequestMapping("/api/payments/mercadopago")
public class MercadoPagoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    @Autowired
    private BillingService billingService;

    @Autowired
    private PortalSubscriptionService portalSubscriptionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "topic", required = false) String topic,
            @RequestParam(name = "data.id", required = false) String dataIdParam,
            @RequestParam(name = "id", required = false) String idParam,
            @RequestBody(required = false) String rawBody) {

        try {
            String eventType = type != null ? type : topic;
            String paymentId = dataIdParam != null ? dataIdParam : idParam;

            if (rawBody != null && !rawBody.isBlank()) {
                JsonNode body = objectMapper.readTree(rawBody);
                if (eventType == null) {
                    eventType = body.path("type").asText(body.path("topic").asText(null));
                }
                if (paymentId == null) {
                    paymentId = body.path("data").path("id").asText(null);
                }
            }

            boolean haveId = paymentId != null && !paymentId.isBlank();
            if ("payment".equalsIgnoreCase(eventType) && haveId) {
                // Pode ser uma comissão OU uma mensalidade de plataforma -- cada
                // serviço ignora o que não é seu.
                billingService.handleWebhook(paymentId);
                portalSubscriptionService.handlePaymentWebhook(paymentId);
            } else if ("subscription_authorized_payment".equalsIgnoreCase(eventType) && haveId) {
                portalSubscriptionService.handleAuthorizedPaymentWebhook(paymentId);
            } else if ("subscription_preapproval".equalsIgnoreCase(eventType)) {
                log.debug("Webhook MP subscription_preapproval (id={}) — sem ação.", paymentId);
            } else {
                log.debug("Webhook MP ignorado (type={}, id={}).", eventType, paymentId);
            }
        } catch (Exception e) {
            // Nunca propaga -- o MP re-tenta em erro, e o job de varredura cobre o resto.
            log.warn("Falha ao processar webhook do Mercado Pago: {}", e.getMessage());
        }
        return ResponseEntity.ok("ok");
    }
}
