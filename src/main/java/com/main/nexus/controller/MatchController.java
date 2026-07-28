package com.main.nexus.controller;

import com.main.nexus.dto.MatchHistoryDTO;
import com.main.nexus.dto.MatchResponseDTO;
import com.main.nexus.dto.ProfessionalSummaryDTO;
import com.main.nexus.dto.ProjectResponseDTO;
import com.main.nexus.dto.PublicCompanyDTO;
import com.main.nexus.dto.SkillResponseDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.MatchHistory;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.enums.CompanyRejectionReason;
import com.main.nexus.model.enums.ProfessionalRejectionReason;
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
    public ResponseEntity<MatchResponseDTO> findById(@PathVariable Long matchId) {
        return ResponseEntity.ok(toMatchResponseDTO(matchService.findById(matchId)));
    }

    @GetMapping("/{matchId}/history")
    public ResponseEntity<List<MatchHistoryDTO>> getHistory(@PathVariable Long matchId) {
        List<MatchHistoryDTO> history = matchService.getHistory(matchId)
                .stream()
                .map(h -> new MatchHistoryDTO(
                        h.getFromStatus(),
                        h.getToStatus(),
                        h.getChangedBy(),
                        h.getChangedAt()))
                .toList();
        return ResponseEntity.ok(history);
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
            @RequestBody List<CompanyRejectionReason> reasons) {
        Long companyId = getLoggedCompanyId();
        matchService.companyRejectsWithFeedback(matchId, companyId, reasons);
        return ResponseEntity.ok("Invite rejected.");
    }

    @PostMapping("/{matchId}/company-cancel")
    public ResponseEntity<String> companyCancels(@PathVariable Long matchId) {
        Long companyId = getLoggedCompanyId();
        matchService.companyCancelsMatch(matchId, companyId);
        return ResponseEntity.ok("Match cancelled.");
    }

    // ── Cancelamento genérico — acessível por empresa ou profissional dono do match ──

    @PostMapping("/{matchId}/cancel")
    public ResponseEntity<String> cancelMatch(@PathVariable Long matchId) {
        UserDTO logged = getLoggedUser();

        Long companyId = null;
        Long professionalId = null;
        String changedBy;

        if ("COMPANY".equals(logged.role())) {
            companyId = getLoggedCompanyId();
            changedBy = "COMPANY";
        } else if ("PROFESSIONAL".equals(logged.role())) {
            professionalId = getLoggedProfessionalId();
            changedBy = "PROFESSIONAL";
        } else {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "Only companies or professionals can cancel a match.");
        }

        matchService.cancelMatch(matchId, companyId, professionalId, changedBy);
        return ResponseEntity.ok("Match cancelled.");
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
            @RequestBody List<ProfessionalRejectionReason> reasons) {
        Long professionalId = getLoggedProfessionalId();
        matchService.professionalRejectsWithFeedback(matchId, professionalId, reasons);
        return ResponseEntity.ok("Invite rejected.");
    }

    // ── Conversão para DTO ───────────────────────────────────────────────────

    private MatchResponseDTO toMatchResponseDTO(Match m) {
        Professional professional = m.getProfessional();
        return new MatchResponseDTO(
                m.getId(),
                m.getMatchScore(),
                m.getCompanyStatus(),
                m.getProfessionalStatus(),
                m.getStatus(),
                m.getCreatedAt(),
                toProjectResponseDTO(m.getProject()),
                new ProfessionalSummaryDTO(
                        professional.getId(),
                        professional.getName(),
                        professional.getPhone(),
                        professional.getReputation(),
                        professional.getProfilePhotoUrl()
                ),
                null,
                m.getActive()
        );
    }

    private ProjectResponseDTO toProjectResponseDTO(Project p) {
        Company c = p.getCompany();
        PublicCompanyDTO companyDTO = new PublicCompanyDTO(
                c.getId(),
                c.getCompanyName(),
                c.getDescription(),
                c.getCity(),
                c.getUf(),
                c.getReputation(),
                c.getProfilePhotoUrl(),
                null,
                c.getTaxId(),
                c.getStatus().name(),
                List.of(),
                c.getUser() != null ? c.getUser().getEmail() : null
        );
        return new ProjectResponseDTO(
                p.getId(),
                p.getTitle(),
                p.getDescription(),
                p.getWorkMode(),
                p.getExperienceLevel(),
                p.getStatus(),
                p.getMaxPositions(),
                p.getFilledPositions(),
                p.getCreatedAt(),
                p.getOpportunityType(),
                p.getRequiredSkills().stream()
                        .map(skill -> new SkillResponseDTO(
                                skill.getId(),
                                skill.getName(),
                                skill.getCategory()
                        ))
                        .toList(),
                c.getId(),
                c.getCompanyName(),

                p.getCep() != null ? p.getCep() : c.getCep(),
                p.getEffectiveLatitude(),
                p.getEffectiveLongitude(),
                p.getEffectiveCity(),
                p.getEffectiveUf(),

                p.getMinimumBudget(),
                p.getMaximumBudget(),
                p.getDeadline(),

                p.getMonthlySalaryMin(),
                p.getMonthlySalaryMax(),
                p.getContractType(),
                p.getBenefits(),
                p.getStartDate(),
                p.getWorkloadHoursPerWeek(),
                companyDTO
        );
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