package com.main.nexus.ratelimit;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 429 lancado de dentro de um service (bloqueio de login em
 * {@code AuthService.login}, cota de IA em {@code ProjectAiExtractionService}).
 * Passa pelo {@code ApiExceptionHandler}, que le {@link #getRetryAfterSeconds()}
 * e {@link #getErrorCode()} para montar o header {@code Retry-After} e o corpo
 * no mesmo formato que o {@link RateLimitFilter} produz.
 *
 * <p>Estende {@code ResponseStatusException} de proposito: se o handler
 * especifico nao pegar por algum motivo, o generico ja existente ainda devolve
 * um {@code {status:429, message}} decente.
 */
public class RateLimitExceededException extends ResponseStatusException {

    /** Diferencia "limite de requisicoes" de "conta bloqueada por login". */
    public static final String REQUESTS = "RATE_LIMIT_EXCEEDED";
    public static final String LOGIN_BLOCKED = "LOGIN_TEMPORARILY_BLOCKED";

    private final long retryAfterSeconds;
    private final String errorCode;

    public RateLimitExceededException(long retryAfterSeconds, String errorCode, String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = retryAfterSeconds;
        this.errorCode = errorCode;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
