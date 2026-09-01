package com.main.nexus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
 * Cliente REST fino do eNotas Gateway para emissao de NFS-e (Prompt 6). Mesmo
 * desenho de MercadoPagoClient: RestTemplate cru + ObjectMapper, api-key por
 * @Value, ResponseStatusException(502) no erro. Auth: Basic base64(apiKey + ":").
 */
@Service
public class EnotasClient {

    private static final Logger log = LoggerFactory.getLogger(EnotasClient.class);

    @Value("${enotas.base-url}")
    private String baseUrl;

    @Value("${enotas.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean hasCredentials() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Cria uma NFS-e e devolve o id dela no eNotas. O eNotas processa de forma
     * assincrona -- o status final vem pelo webhook / consulta (getNfse).
     */
    public String createNfse(String empresaId, Map<String, Object> body) {
        JsonNode res = exchange(HttpMethod.POST,
                "/v2/empresas/" + empresaId + "/nfes", body);
        // O v2 pode devolver o id como string pura ("guid") ou dentro de um objeto.
        if (res.isTextual()) {
            return res.asText();
        }
        for (String field : new String[]{"NfeId", "nfeId", "id", "Id"}) {
            String v = res.path(field).asText(null);
            if (v != null && !v.isBlank()) return v;
        }
        throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                "eNotas nao retornou o id da NFS-e criada.");
    }

    public EnotasNfe getNfse(String empresaId, String nfeId) {
        JsonNode r = exchange(HttpMethod.GET,
                "/v2/empresas/" + empresaId + "/nfes/" + nfeId, null);
        return new EnotasNfe(
                firstNonBlank(r, "id", "Id"),
                firstNonBlank(r, "status", "Status"),
                firstNonBlank(r, "numero", "Numero"),
                firstNonBlank(r, "serie", "Serie"),
                firstNonBlank(r, "linkDownloadPDF", "LinkDownloadPDF"),
                firstNonBlank(r, "linkDownloadXML", "LinkDownloadXML"),
                firstNonBlank(r, "codigoVerificacao", "CodigoVerificacao"),
                firstNonBlank(r, "motivoStatus", "MotivoStatus"));
    }

    // ── HTTP ───────────────────────────────────────────────────────

    private JsonNode exchange(HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Basic "
                + Base64.getEncoder().encodeToString((apiKey + ":").getBytes(StandardCharsets.UTF_8)));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Object> request = new HttpEntity<>(body, headers);
        try {
            String response = restTemplate.exchange(baseUrl + path, method, request, String.class).getBody();
            String trimmed = response == null ? "" : response.trim();
            if (trimmed.isEmpty()) return objectMapper.createObjectNode();
            // Corpo pode ser um GUID sem aspas (POST .../nfes) -- embrulha como string JSON.
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[") && !trimmed.startsWith("\"")) {
                return objectMapper.getNodeFactory().textNode(trimmed);
            }
            return objectMapper.readTree(trimmed);
        } catch (HttpClientErrorException e) {
            String errBody = e.getResponseBodyAsString();
            log.warn("eNotas {} {} -> {} {}", method, path, e.getStatusCode(), errBody);
            // 4xx (dado invalido) -- 422 para o NfseService classificar como "falha
            // de dados fiscais" e mandar para a fila, sem retry infinito.
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "eNotas recusou a emissao: " + shorten(errBody), e);
        } catch (Exception e) {
            log.error("eNotas {} {} falhou: {}", method, path, e.getMessage());
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "Falha de comunicacao com o eNotas.", e);
        }
    }

    private String firstNonBlank(JsonNode node, String... fields) {
        for (String f : fields) {
            String v = node.path(f).asText(null);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String shorten(String s) {
        if (s == null) return "";
        return s.length() > 400 ? s.substring(0, 400) + "..." : s;
    }

    public record EnotasNfe(
            String id,
            String status,
            String numero,
            String serie,
            String linkPdf,
            String linkXml,
            String codigoVerificacao,
            String motivo) {}
}
