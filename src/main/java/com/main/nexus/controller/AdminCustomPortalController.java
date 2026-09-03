package com.main.nexus.controller;

import com.main.nexus.config.AuditDataAccess;
import com.main.nexus.model.enums.AuditTargetType;
import com.main.nexus.dto.AdminCustomPortalAnalyticsDTO;
import com.main.nexus.dto.ApproveCustomPortalRequestDTO;
import com.main.nexus.dto.CreateCustomPortalDTO;
import com.main.nexus.dto.CustomPortalAnalyticsDTO;
import com.main.nexus.dto.CustomPortalDTO;
import com.main.nexus.dto.CustomPortalDetailDTO;
import com.main.nexus.dto.CustomPortalRequestDTO;
import com.main.nexus.dto.CustomPortalStatusChangeDTO;
import com.main.nexus.dto.RejectCustomPortalRequestDTO;
import com.main.nexus.dto.UpdateCustomPortalBrandingDTO;
import com.main.nexus.dto.UpdateCustomPortalSubscriptionDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.enums.BrandingImageKind;
import com.main.nexus.model.enums.CustomPortalRequestStatus;
import com.main.nexus.service.CustomPortalAnalyticsService;
import com.main.nexus.service.CustomPortalService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// Painel Admin da plataforma personalizada: fila de solicitacoes + gestao das
// plataformas (criacao direta, ciclo de vida, assinatura). Protegido por
// hasRole("ADMIN") em SecurityConfig (regra generica /api/admin/**).
@RestController
@RequestMapping("/api/admin")
public class AdminCustomPortalController {

    @Autowired
    private CustomPortalService customPortalService;

    @Autowired
    private CustomPortalAnalyticsService customPortalAnalyticsService;

    // ── Solicitações ──────────────────────────────────────────────────

    @GetMapping("/custom-portal-requests")
    public ResponseEntity<List<CustomPortalRequestDTO>> listRequests(
            @RequestParam(required = false) CustomPortalRequestStatus status) {
        return ResponseEntity.ok(customPortalService.listRequests(status));
    }

    @AuditDataAccess(action = "Aprovou a solicitação de plataforma personalizada do contratante",
            target = AuditTargetType.CUSTOM_PORTAL_REQUEST, param = "requestId")
    @PostMapping("/custom-portal-requests/{requestId}/approve")
    public ResponseEntity<CustomPortalDTO> approveRequest(
            @PathVariable Long requestId,
            @RequestBody ApproveCustomPortalRequestDTO body) {
        return ResponseEntity.ok(
                customPortalService.approveRequest(loggedUserId(), requestId, body));
    }

    @AuditDataAccess(action = "Rejeitou a solicitação de plataforma personalizada do contratante",
            target = AuditTargetType.CUSTOM_PORTAL_REQUEST, param = "requestId")
    @PostMapping("/custom-portal-requests/{requestId}/reject")
    public ResponseEntity<CustomPortalRequestDTO> rejectRequest(
            @PathVariable Long requestId,
            @RequestBody RejectCustomPortalRequestDTO body) {
        return ResponseEntity.ok(customPortalService.rejectRequest(
                loggedUserId(), requestId, body != null ? body.reason() : null));
    }

    // ── Plataformas ───────────────────────────────────────────────────

    @GetMapping("/custom-portals")
    public ResponseEntity<List<CustomPortalDTO>> listPortals() {
        return ResponseEntity.ok(customPortalService.listPortals());
    }

    // Dashboard geral do módulo — agregado de todas as plataformas. Caminho
    // literal, resolve antes de /custom-portals/{portalId}.
    @GetMapping("/custom-portals/analytics")
    public ResponseEntity<AdminCustomPortalAnalyticsDTO> systemAnalytics(
            @RequestParam(name = "days", defaultValue = "30") int days) {
        return ResponseEntity.ok(customPortalAnalyticsService.getSystemAnalytics(days));
    }

    @AuditDataAccess(action = "Visualizou os detalhes da plataforma personalizada do contratante",
            target = AuditTargetType.CUSTOM_PORTAL, param = "portalId")
    @GetMapping("/custom-portals/{portalId}")
    public ResponseEntity<CustomPortalDetailDTO> portalDetail(@PathVariable Long portalId) {
        return ResponseEntity.ok(customPortalService.getPortalDetail(portalId));
    }

    // Mesmo dashboard "Análises" do contratante, para uma plataforma qualquer.
    @AuditDataAccess(action = "Visualizou os analytics da plataforma personalizada do contratante",
            target = AuditTargetType.CUSTOM_PORTAL, param = "portalId")
    @GetMapping("/custom-portals/{portalId}/analytics")
    public ResponseEntity<CustomPortalAnalyticsDTO> portalAnalytics(
            @PathVariable Long portalId,
            @RequestParam(name = "days", defaultValue = "30") int days) {
        return ResponseEntity.ok(
                customPortalAnalyticsService.getAnalyticsForPortal(portalId, days));
    }

    @PostMapping("/custom-portals")
    public ResponseEntity<CustomPortalDTO> createPortal(
            @RequestBody CreateCustomPortalDTO body) {
        return ResponseEntity.ok(
                customPortalService.createPortalDirectly(loggedUserId(), body));
    }

    @PostMapping("/custom-portals/{portalId}/suspend")
    public ResponseEntity<CustomPortalDTO> suspend(
            @PathVariable Long portalId,
            @RequestBody(required = false) CustomPortalStatusChangeDTO body) {
        return ResponseEntity.ok(customPortalService.suspend(
                loggedUserId(), portalId, body != null ? body.note() : null));
    }

    @PostMapping("/custom-portals/{portalId}/reactivate")
    public ResponseEntity<CustomPortalDTO> reactivate(
            @PathVariable Long portalId,
            @RequestBody(required = false) CustomPortalStatusChangeDTO body) {
        return ResponseEntity.ok(customPortalService.reactivate(
                loggedUserId(), portalId, body != null ? body.note() : null));
    }

    @PostMapping("/custom-portals/{portalId}/cancel")
    public ResponseEntity<CustomPortalDTO> cancel(
            @PathVariable Long portalId,
            @RequestBody(required = false) CustomPortalStatusChangeDTO body) {
        return ResponseEntity.ok(customPortalService.cancel(
                loggedUserId(), portalId, body != null ? body.note() : null));
    }

    @PutMapping("/custom-portals/{portalId}/subscription")
    public ResponseEntity<CustomPortalDTO> updateSubscription(
            @PathVariable Long portalId,
            @RequestBody UpdateCustomPortalSubscriptionDTO body) {
        return ResponseEntity.ok(
                customPortalService.updateSubscription(portalId, body));
    }

    // ── Customização visual (Prompt 2) — mesmas ações do contratante ──

    @PutMapping("/custom-portals/{portalId}/branding")
    public ResponseEntity<CustomPortalDTO> updateBranding(
            @PathVariable Long portalId,
            @RequestBody UpdateCustomPortalBrandingDTO body) {
        return ResponseEntity.ok(
                customPortalService.updateBrandingAsAdmin(portalId, body));
    }

    @PostMapping("/custom-portals/{portalId}/branding/image")
    public ResponseEntity<CustomPortalDTO> uploadBrandingImage(
            @PathVariable Long portalId,
            @RequestParam("kind") BrandingImageKind kind,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(
                customPortalService.setBrandingImageAsAdmin(portalId, kind, file));
    }

    @DeleteMapping("/custom-portals/{portalId}/branding/image")
    public ResponseEntity<CustomPortalDTO> deleteBrandingImage(
            @PathVariable Long portalId,
            @RequestParam("kind") BrandingImageKind kind) {
        return ResponseEntity.ok(
                customPortalService.clearBrandingImageAsAdmin(portalId, kind));
    }

    private Long loggedUserId() {
        UserDTO logged = (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return logged.id();
    }
}
