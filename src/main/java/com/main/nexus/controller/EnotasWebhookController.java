package com.main.nexus.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.nexus.service.NfseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Webhook do eNotas (Prompt 6). Público (permitAll em SecurityConfig). O eNotas
// notifica a mudança de status de uma NFS-e; aqui só extraímos o id da nota e
// re-consultamos o eNotas (NfseService.handleWebhook) para pegar o status final.
// Sempre responde 200 -- o job de varredura cobre o que escapar.
@RestController
@RequestMapping("/api/invoices/enotas")
public class EnotasWebhookController {

    private static final Logger log = LoggerFactory.getLogger(EnotasWebhookController.class);

    @Autowired
    private NfseService nfseService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestParam(name = "nfeId", required = false) String nfeIdParam,
            @RequestParam(name = "id", required = false) String idParam,
            @RequestBody(required = false) String rawBody) {

        try {
            String nfeId = firstNonBlank(nfeIdParam, idParam);
            if (nfeId == null && rawBody != null && !rawBody.isBlank()) {
                JsonNode body = objectMapper.readTree(rawBody);
                nfeId = firstNonBlank(
                        body.path("nfeId").asText(null),
                        body.path("NfeId").asText(null),
                        body.path("id").asText(null),
                        body.path("Id").asText(null),
                        body.path("data").path("nfeId").asText(null),
                        body.path("data").path("id").asText(null));
            }
            if (nfeId != null && !nfeId.isBlank()) {
                nfseService.handleWebhook(nfeId);
            } else {
                log.debug("Webhook eNotas sem id de nota identificável.");
            }
        } catch (Exception e) {
            log.warn("Falha ao processar webhook do eNotas: {}", e.getMessage());
        }
        return ResponseEntity.ok("ok");
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
