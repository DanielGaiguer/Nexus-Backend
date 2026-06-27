package com.main.nexus.controller;

import com.main.nexus.dto.UserDTO;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.ProfessionalService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    @Autowired
    private MatchService matchService;

    @Autowired
    private ProfessionalService professionalService;

    @Autowired
    private CompanyService companyService;

    @GetMapping("/{matchId}")
    public ResponseEntity<?> findById(@PathVariable Long matchId) {
        return ResponseEntity.ok(matchService.findById(matchId));
    }

    // ── Empresa demonstra interesse (primeiro contato) ──────────────────────

    @PostMapping("/{matchId}/company-interest")
    public ResponseEntity<String> companyShowsInterest(@PathVariable Long matchId) {
        Long companyId = getLoggedCompanyId();
        matchService.companyShowsInterest(matchId, companyId);
        return ResponseEntity.ok("Interest sent to professional.");
    }

    // ── Empresa responde a um interesse do profissional ─────────────────────

    @PostMapping("/{matchId}/company-accept")
    public ResponseEntity<String> companyAccepts(@PathVariable Long matchId) {
        Long companyId = getLoggedCompanyId();
        matchService.companyAccepts(matchId, companyId);
        return ResponseEntity.ok("Match confirmed! Contact details are now available.");
    }

    @PostMapping("/{matchId}/company-reject")
    public ResponseEntity<String> companyRejects(
            @PathVariable Long matchId,
            @RequestBody List<String> reasons) {
        Long companyId = getLoggedCompanyId();
        matchService.companyRejectsWithFeedback(matchId, companyId, reasons);
        return ResponseEntity.ok("Invite rejected.");
    }

    // ── Profissional responde a um interesse da empresa ─────────────────────

    @PostMapping("/{matchId}/professional-accept")
    public ResponseEntity<String> professionalAccepts(@PathVariable Long matchId) {
        Long professionalId = getLoggedProfessionalId();
        matchService.professionalAccepts(matchId, professionalId);
        return ResponseEntity.ok("Match confirmed! Contact details are now available.");
    }

    @PostMapping("/{matchId}/professional-reject")
    public ResponseEntity<String> professionalRejects(
            @PathVariable Long matchId,
            @RequestBody List<String> reasons) {
        Long professionalId = getLoggedProfessionalId();
        matchService.professionalRejectsWithFeedback(matchId, professionalId, reasons);
        return ResponseEntity.ok("Invite rejected.");
    }

    // ── Utilitários de identidade ────────────────────────────────────────────

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    private Long getLoggedCompanyId() {
        UserDTO logged = getLoggedUser();
        return companyService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company profile not found"))
                .getId();
    }

    private Long getLoggedProfessionalId() {
        UserDTO logged = getLoggedUser();
        return professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional profile not found"))
                .getId();
    }
}