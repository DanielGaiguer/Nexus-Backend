package com.main.nexus.controller;

import com.main.nexus.dto.BillingModeDTO;
import com.main.nexus.dto.PortalSubscriptionChargeDTO;
import com.main.nexus.dto.SimulateChargeRequestDTO;
import com.main.nexus.service.PortalSubscriptionService;
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

// Supervisão do Admin sobre as mensalidades da plataforma personalizada.
// /api/admin/** = hasRole("ADMIN").
@RestController
@RequestMapping("/api/admin/portal-subscription-charges")
public class AdminPortalSubscriptionChargeController {

    @Autowired
    private PortalSubscriptionService portalSubscriptionService;

    @GetMapping
    public ResponseEntity<List<PortalSubscriptionChargeDTO>> list(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(portalSubscriptionService.listForAdmin(status));
    }

    // Diz ao painel se a simulação está disponível (modo simulate, sem Mercado Pago).
    @GetMapping("/mode")
    public ResponseEntity<BillingModeDTO> mode() {
        return ResponseEntity.ok(portalSubscriptionService.mode());
    }

    // Modo simulate: o Admin decide o resultado da cobrança. outcome = approved | rejected.
    @PostMapping("/{id}/simulate")
    public ResponseEntity<PortalSubscriptionChargeDTO> simulate(
            @PathVariable Long id,
            @RequestBody SimulateChargeRequestDTO body) {
        return ResponseEntity.ok(portalSubscriptionService.simulateOutcome(id, body.outcome()));
    }
}
