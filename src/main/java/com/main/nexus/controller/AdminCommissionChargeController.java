package com.main.nexus.controller;

import com.main.nexus.config.AuditDataAccess;
import com.main.nexus.model.enums.AuditTargetType;
import com.main.nexus.dto.BillingModeDTO;
import com.main.nexus.dto.CommissionChargeDTO;
import com.main.nexus.dto.SimulateChargeRequestDTO;
import com.main.nexus.service.BillingService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Supervisão do Admin sobre as cobranças de comissão (Prompt 5). /api/admin/** = hasRole("ADMIN").
@RestController
@RequestMapping("/api/admin/commission-charges")
public class AdminCommissionChargeController {

    @Autowired
    private BillingService billingService;

    @AuditDataAccess(action = "Consultou a fila de cobranças de comissão", target = AuditTargetType.NONE)
    @GetMapping
    public ResponseEntity<List<CommissionChargeDTO>> list(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(billingService.listForAdmin(status));
    }

    // Diz ao painel se a simulação está disponível (modo simulate, sem Mercado Pago).
    @GetMapping("/mode")
    public ResponseEntity<BillingModeDTO> mode() {
        return ResponseEntity.ok(billingService.mode());
    }

    // Modo simulate: o Admin decide o resultado da cobrança. outcome = approved | rejected.
    @PostMapping("/{id}/simulate")
    public ResponseEntity<CommissionChargeDTO> simulate(
            @PathVariable Long id,
            @RequestBody SimulateChargeRequestDTO body) {
        return ResponseEntity.ok(billingService.simulateOutcome(id, body.outcome()));
    }
}
