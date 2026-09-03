package com.main.nexus.controller;

import com.main.nexus.dto.DataAccessLogPageDTO;
import com.main.nexus.service.DataAccessLogService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Consulta do log de auditoria de acesso administrativo a dado pessoal (LGPD).
// Protegido pela regra genérica /api/admin/** (hasRole("ADMIN")). Só leitura --
// não há endpoint de escrita/remoção (o log é imutável).
@RestController
@RequestMapping("/api/admin/data-access-logs")
public class DataAccessLogController {

    @Autowired
    private DataAccessLogService dataAccessLogService;

    @GetMapping
    public ResponseEntity<DataAccessLogPageDTO> search(
            @RequestParam(required = false) Long adminUserId,
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDt = to != null ? to.atTime(LocalTime.MAX) : null;

        return ResponseEntity.ok(dataAccessLogService.search(
                adminUserId, targetUserId, fromDt, toDt, page, size));
    }
}
