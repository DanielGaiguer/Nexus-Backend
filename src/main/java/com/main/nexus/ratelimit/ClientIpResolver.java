package com.main.nexus.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * IP do cliente para as politicas com chave por IP (publicas e de autenticacao).
 *
 * <p>Hoje todo o trafego real chega pelo BFF (nexus-frontend-next), que fala com
 * este backend server-to-server a partir de {@code 127.0.0.1} e repassa o IP
 * original no header {@code X-Forwarded-For} (ver {@code backendFetch} em
 * {@code src/lib/api-client.ts}). Sem confiar nesse header, "chave = IP"
 * colapsaria num balde unico para todo mundo.
 *
 * <p>HARDENING: confiamos no {@code X-Forwarded-For} porque, hoje, a unica porta
 * de entrada deste backend e o BFF -- ele nao esta exposto a trafego publico
 * arbitrario por fora. Se um dia passar a estar, este resolver precisa validar a
 * cadeia (numero de proxies confiaveis / allowlist do BFF) antes de aceitar o
 * primeiro IP do header, senao qualquer cliente forja o proprio IP.
 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // "client, proxy1, proxy2" -- o primeiro e o cliente original.
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            first = first.trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
