package com.main.nexus.controller;

import com.main.nexus.dto.CreateCustomPortalRequestDTO;
import com.main.nexus.dto.CustomPortalAnalyticsDTO;
import com.main.nexus.dto.CustomPortalDTO;
import com.main.nexus.dto.CustomPortalOverviewDTO;
import com.main.nexus.dto.CustomPortalRequestDTO;
import com.main.nexus.dto.PortalSubscriptionChargeDTO;
import com.main.nexus.dto.PortalSubscriptionStatusDTO;
import com.main.nexus.dto.SaveCardRequestDTO;
import com.main.nexus.dto.UpdateCustomPortalBrandingDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.enums.BrandingImageKind;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.CustomPortalAnalyticsService;
import com.main.nexus.service.CustomPortalService;
import com.main.nexus.service.PortalSubscriptionService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

// Lado do contratante da plataforma personalizada. Protegido por
// hasRole("COMPANY") em SecurityConfig (regra generica /api/company/**).
@RestController
@RequestMapping("/api/company/custom-portal")
public class CustomPortalController {

    @Autowired
    private CustomPortalService customPortalService;

    @Autowired
    private CustomPortalAnalyticsService customPortalAnalyticsService;

    @Autowired
    private PortalSubscriptionService portalSubscriptionService;

    @Autowired
    private CompanyService companyService;

    // Estado da plataforma personalizada do contratante logado: ultima
    // solicitacao, portal (se ja existir) e se ele pode abrir nova solicitacao.
    @GetMapping
    public ResponseEntity<CustomPortalOverviewDTO> overview() {
        return ResponseEntity.ok(
                customPortalService.getOverviewForUser(loggedUserId()));
    }

    // Registra a solicitacao de interesse. 409 se ja houver uma pendente ou se
    // o contratante ja tiver um portal.
    @PostMapping("/requests")
    public ResponseEntity<CustomPortalRequestDTO> createRequest(
            @RequestBody(required = false) CreateCustomPortalRequestDTO body) {
        String message = body != null ? body.message() : null;
        return ResponseEntity.ok(
                customPortalService.createRequest(loggedUserId(), message));
    }

    // ── Customização visual (Prompt 2) ───────────────────────────────

    @PutMapping("/branding")
    public ResponseEntity<CustomPortalDTO> updateBranding(
            @RequestBody UpdateCustomPortalBrandingDTO body) {
        return ResponseEntity.ok(
                customPortalService.updateBrandingForUser(loggedUserId(), body));
    }

    @PostMapping("/branding/image")
    public ResponseEntity<CustomPortalDTO> uploadBrandingImage(
            @RequestParam("kind") BrandingImageKind kind,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(
                customPortalService.setBrandingImageForUser(loggedUserId(), kind, file));
    }

    @DeleteMapping("/branding/image")
    public ResponseEntity<CustomPortalDTO> deleteBrandingImage(
            @RequestParam("kind") BrandingImageKind kind) {
        return ResponseEntity.ok(
                customPortalService.clearBrandingImageForUser(loggedUserId(), kind));
    }

    // ── Assinatura & cobrança (mensalidade da plataforma) ────────────

    @GetMapping("/subscription")
    public ResponseEntity<PortalSubscriptionStatusDTO> subscriptionStatus() {
        return ResponseEntity.ok(portalSubscriptionService.getStatus(loggedCompany()));
    }

    // O cartão da assinatura é tokenizado no frontend (Card Brick) -- aqui só
    // chega o token. O backend nunca vê número/CVV/validade.
    @PostMapping("/subscription/card")
    public ResponseEntity<PortalSubscriptionStatusDTO> saveSubscriptionCard(
            @RequestBody SaveCardRequestDTO body) {
        return ResponseEntity.ok(portalSubscriptionService.saveCard(
                loggedCompany(), body != null ? body.cardToken() : null));
    }

    @GetMapping("/subscription/charges")
    public ResponseEntity<List<PortalSubscriptionChargeDTO>> subscriptionCharges() {
        return ResponseEntity.ok(portalSubscriptionService.chargesForCompany(loggedCompany()));
    }

    // ── Análises (dashboard do contratante) ──────────────────────────

    @GetMapping("/analytics")
    public ResponseEntity<CustomPortalAnalyticsDTO> analytics(
            @RequestParam(name = "days", defaultValue = "30") int days) {
        return ResponseEntity.ok(
                customPortalAnalyticsService.getAnalyticsForUser(loggedUserId(), days));
    }

    private Long loggedUserId() {
        UserDTO logged = (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return logged.id();
    }

    private Company loggedCompany() {
        return companyService.findByUserId(loggedUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company profile not found"));
    }
}
