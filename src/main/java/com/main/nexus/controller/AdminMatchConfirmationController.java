package com.main.nexus.controller;

import com.main.nexus.dto.AdminCompanyConfirmationOverviewDTO;
import com.main.nexus.dto.AdminCompanyObservationRequestDTO;
import com.main.nexus.dto.AdminConfirmationQueueItemDTO;
import com.main.nexus.dto.AdminConfirmationReviewRequestDTO;
import com.main.nexus.dto.AdminMatchConfirmationDTO;
import com.main.nexus.dto.AdminResolveConfirmationRequestDTO;
import com.main.nexus.dto.AdminUnconfirmableRequestDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.service.MatchConfirmationAdminService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Supervisão do Admin sobre as confirmações pós-contratação (Prompt 2).
// Protegido pela regra genérica /api/admin/** (hasRole("ADMIN")).
@RestController
@RequestMapping("/api/admin")
public class AdminMatchConfirmationController {

    @Autowired
    private MatchConfirmationAdminService adminService;

    // Fila de atenção -- empresas com confirmações que pedem avaliação manual.
    @GetMapping("/confirmations/queue")
    public ResponseEntity<List<AdminConfirmationQueueItemDTO>> queue() {
        return ResponseEntity.ok(adminService.queue());
    }

    // Lista completa, filtrável por status (?status=PENDING_ADMIN_REVIEW etc.) e/ou empresa.
    @GetMapping("/confirmations")
    public ResponseEntity<List<AdminMatchConfirmationDTO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long companyId) {
        return ResponseEntity.ok(adminService.list(status, companyId));
    }

    // Fila dedicada de reconciliação -- casos PENDING_ADMIN_REVIEW, mais antigos primeiro (Prompt 3).
    @GetMapping("/confirmations/pending-reconciliation")
    public ResponseEntity<List<AdminMatchConfirmationDTO>> pendingReconciliation() {
        return ResponseEntity.ok(adminService.pendingReconciliation());
    }

    // Panorama das confirmações de uma empresa (drill-down).
    @GetMapping("/companies/{companyId}/confirmations")
    public ResponseEntity<AdminCompanyConfirmationOverviewDTO> companyOverview(
            @PathVariable Long companyId) {
        return ResponseEntity.ok(adminService.companyOverview(companyId));
    }

    // Marca um caso como revisado + nota.
    @PostMapping("/confirmations/{matchId}/review")
    public ResponseEntity<AdminMatchConfirmationDTO> review(
            @PathVariable Long matchId,
            @RequestBody(required = false) AdminConfirmationReviewRequestDTO body) {
        return ResponseEntity.ok(adminService.markReviewed(
                matchId, loggedUserId(), body != null ? body.note() : null));
    }

    // Reconciliação manual (Prompt 3): Admin define o valor final -> vira definitivo p/ comissão.
    @PostMapping("/confirmations/{matchId}/resolve")
    public ResponseEntity<AdminMatchConfirmationDTO> resolve(
            @PathVariable Long matchId,
            @RequestBody AdminResolveConfirmationRequestDTO body) {
        return ResponseEntity.ok(adminService.resolveWithValue(
                matchId, body.finalAmount(), loggedUserId(), body.note()));
    }

    // Reconciliação manual (Prompt 3): não foi possível confirmar -> sem valor, sem comissão.
    @PostMapping("/confirmations/{matchId}/mark-unconfirmable")
    public ResponseEntity<AdminMatchConfirmationDTO> markUnconfirmable(
            @PathVariable Long matchId,
            @RequestBody(required = false) AdminUnconfirmableRequestDTO body) {
        return ResponseEntity.ok(adminService.resolveUnconfirmable(
                matchId, loggedUserId(), body != null ? body.note() : null));
    }

    // Liga/desliga o flag "empresa sob observação".
    @PutMapping("/companies/{companyId}/observation")
    public ResponseEntity<AdminCompanyConfirmationOverviewDTO> setObservation(
            @PathVariable Long companyId,
            @RequestBody AdminCompanyObservationRequestDTO body) {
        return ResponseEntity.ok(adminService.setObservation(companyId, body.underObservation()));
    }

    private Long loggedUserId() {
        UserDTO logged = (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return logged.id();
    }
}
