package com.main.nexus.controller;

import com.main.nexus.dto.AdminFinanceOverviewDTO;
import com.main.nexus.service.FinanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Painel financeiro do Admin (Prompt 7): KPIs de receita de comissão + gráfico
// mensal + contagens das filas (reconciliação, NFS-e, cobranças). As telas de
// trabalho ficam nos Prompts 3/5/6. /api/admin/** já é hasRole("ADMIN").
@RestController
@RequestMapping("/api/admin/finance")
public class AdminFinanceController {

    @Autowired
    private FinanceService financeService;

    @GetMapping("/overview")
    public ResponseEntity<AdminFinanceOverviewDTO> overview() {
        return ResponseEntity.ok(financeService.adminOverview());
    }
}
