package com.main.nexus.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

// @RestControllerAdvice é um interceptador global de exceções do Spring MVC,
// em vez de cada controller precisar de um try/catch, qualquer ResponseStatusException lançada em qualquer lugar da aplicação 
// (controller ou, mais comumente, dentro de um service chamado por ele) é capturada aqui uma única vez, e traduzida para um corpo JSON padronizado: 
// {"status": 409, "message": "Email already registered..."}
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", ex.getStatusCode().value());
        body.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }
}
