package com.main.nexus.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Escreve a resposta 429 do {@link RateLimitFilter} direto no
 * {@code HttpServletResponse} -- o filtro roda fora do DispatcherServlet, entao
 * nao passa pelo {@code ApiExceptionHandler}. O corpo segue o formato ja
 * padronizado da API ({@code {status, message}}) mais dois campos:
 *
 * <pre>
 *   { "status": 429, "message": "...", "error": "RATE_LIMIT_EXCEEDED", "retryAfter": 42 }
 * </pre>
 *
 * e o header {@code Retry-After} em segundos. O {@code ApiExceptionHandler} monta
 * exatamente esse mesmo shape para os 429 vindos de service.
 */
@Component
public class RateLimitResponseWriter {

    private final ObjectMapper objectMapper;

    public RateLimitResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeTooManyRequests(HttpServletResponse response,
                                     long retryAfterSeconds,
                                     String errorCode,
                                     String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 429);
        body.put("message", message);
        body.put("error", errorCode);
        body.put("retryAfter", retryAfterSeconds);

        objectMapper.writeValue(response.getWriter(), body);
    }
}
