package com.main.nexus.controller;

import com.main.nexus.dto.CompanyRejectRequestDTO;
import com.main.nexus.dto.MatchActionResponseDTO;
import com.main.nexus.dto.MatchHistoryDTO;
import com.main.nexus.dto.MatchResponseDTO;
import com.main.nexus.dto.MatchStatusDTO;
import com.main.nexus.dto.ProfessionalRejectRequestDTO;
import com.main.nexus.dto.ProfessionalSummaryDTO;
import com.main.nexus.dto.ProjectResponseDTO;
import com.main.nexus.dto.PublicCompanyDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.RejectionFeedback;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.MatchActionResult;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.ProfessionalService;
import com.main.nexus.service.ProjectResponseAssembler;
import com.main.nexus.service.ProposalService;
import com.main.nexus.service.ScreeningInvitationService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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

    @Autowired
    private ProjectResponseAssembler projectResponseAssembler;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private ScreeningInvitationService screeningInvitationService;

    @GetMapping("/{matchId}")
    public ResponseEntity<MatchResponseDTO> findById(@PathVariable Long matchId) {
        Match match = matchService.findById(matchId);
        validateParticipant(match);
        return ResponseEntity.ok(toMatchResponseDTO(match));
    }

    @GetMapping("/{matchId}/history")
    public ResponseEntity<List<MatchHistoryDTO>> getHistory(@PathVariable Long matchId) {
        validateParticipant(matchService.findById(matchId));

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

    // Garante que quem está pedindo é a empresa dona do projeto ou o profissional do match —
    // sem isso, qualquer empresa/profissional autenticado conseguia consultar o match de
    // outra pessoa só sabendo o ID.
    private void validateParticipant(Match match) {
        UserDTO logged = getLoggedUser();
        if ("COMPANY".equals(logged.role())) {
            if (!match.getProject().getCompany().getId().equals(getLoggedCompanyId())) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                        "This match does not belong to your company.");
            }
        } else if ("PROFESSIONAL".equals(logged.role())) {
            if (!match.getProfessional().getId().equals(getLoggedProfessionalId())) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                        "This match does not belong to you.");
            }
        } else {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "You are not authorized to view this match.");
        }
    }

    // Empresa demonstra interesse─

    @PostMapping("/{matchId}/company-interest")
    public ResponseEntity<String> companyShowsInterest(@PathVariable Long matchId) {
        Long companyId = getLoggedCompanyId();
        matchService.companyShowsInterest(matchId, companyId);
        return ResponseEntity.ok("Interest sent to professional.");
    }

    // Mesma ideia acima, mas pra quando a empresa demonstra interesse num
    // profissional achado pelo diretório geral (/company/professionals) -- pode
    // não existir match nenhum ainda entre os dois, então não há matchId pra
    // usar no endpoint acima.
    @PostMapping("/company-interest-by-project")
    public ResponseEntity<String> companyShowsInterestByProject(
            @RequestParam Long professionalId,
            @RequestParam Long projectId) {
        Long companyId = getLoggedCompanyId();
        matchService.companyShowsInterestByProject(professionalId, projectId, companyId);
        return ResponseEntity.ok("Interest sent to professional.");
    }

    // Status do match (se existir) entre um profissional e um projeto -- ver
    // MatchService#getStatusByPair.
    @GetMapping("/status-by-pair")
    public ResponseEntity<MatchStatusDTO> statusByPair(
            @RequestParam Long professionalId,
            @RequestParam Long projectId) {
        Long companyId = getLoggedCompanyId();
        return ResponseEntity.ok(
                matchService.getStatusByPair(professionalId, projectId, companyId));
    }

    // Empresa responde a um interesse do profissional

    @PostMapping("/{matchId}/company-accept")
    public ResponseEntity<String> companyAccepts(@PathVariable Long matchId) {
        Long companyId = getLoggedCompanyId();
        matchService.companyAccepts(matchId, companyId);
        return ResponseEntity.ok("Match confirmed! Contact details are now available.");
    }

    @PostMapping("/{matchId}/company-reject")
    public ResponseEntity<String> companyRejects(
            @PathVariable Long matchId,
            @RequestBody CompanyRejectRequestDTO request) {
        Long companyId = getLoggedCompanyId();
        matchService.companyRejectsWithFeedback(
                matchId, companyId, request.reasons(), request.description());
        return ResponseEntity.ok("Invite rejected.");
    }

    @PostMapping("/{matchId}/company-cancel")
    public ResponseEntity<String> companyCancels(@PathVariable Long matchId) {
        Long companyId = getLoggedCompanyId();
        matchService.companyCancelsMatch(matchId, companyId);
        return ResponseEntity.ok("Match cancelled.");
    }

    // Cancelamento genérico

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

    // Profissional responde um interesse 

    @PostMapping("/{matchId}/professional-accept")
    public ResponseEntity<MatchActionResponseDTO> professionalAccepts(@PathVariable Long matchId) {
        Long professionalId = getLoggedProfessionalId();
        MatchActionResult result = matchService.professionalAccepts(matchId, professionalId);
        if (result.screeningRequired()) {
            return ResponseEntity.ok(new MatchActionResponseDTO(
                    "Responda o questionário de triagem da vaga antes de continuar.",
                    true, result.screeningInvitationId()));
        }
        return ResponseEntity.ok(new MatchActionResponseDTO(
                "Match confirmed! Contact details are now available.", false, null));
    }

    @PostMapping("/{matchId}/professional-reject")
    public ResponseEntity<String> professionalRejects(
            @PathVariable Long matchId,
            @RequestBody ProfessionalRejectRequestDTO request) {
        Long professionalId = getLoggedProfessionalId();
        matchService.professionalRejectsWithFeedback(
                matchId, professionalId, request.reasons(), request.description());
        return ResponseEntity.ok("Invite rejected.");
    }

    // Espelho de company-cancel: profissional cancela um match confirmado ou retira um
    // interesse pendente que ele mesmo demonstrou.
    @PostMapping("/{matchId}/professional-cancel")
    public ResponseEntity<String> professionalCancels(@PathVariable Long matchId) {
        Long professionalId = getLoggedProfessionalId();
        matchService.professionalCancelsMatch(matchId, professionalId);
        return ResponseEntity.ok("Match cancelled.");
    }

    // Conversão pra DTO

    private MatchResponseDTO toMatchResponseDTO(Match m) {
        Professional professional = m.getProfessional();
        RejectionFeedback feedback = m.getStatus() == StatusMatch.REJECTED
                ? matchService.getRejectionFeedback(m.getId()) : null;
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
                        professional.getProfilePhotoUrl(),
                        professional.getSkills().stream().map(Skill::getName).toList()
                ),
                null,
                m.getActive(),
                matchService.getRejectionReasonNames(feedback),
                feedback != null ? feedback.getDescription() : null,
                m.getAcceptedProposal() != null ? proposalService.toResponseDTO(m.getAcceptedProposal()) : null,
                screeningInvitationService.getSummariesForMatch(m)
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
                c.getUser() != null ? c.getUser().getEmail() : null,
                c.getType().name()
        );

        UserDTO logged = getLoggedUser();
        ProjectResponseAssembler.Viewer viewer = "COMPANY".equals(logged.role())
                ? ProjectResponseAssembler.Viewer.company(getLoggedCompanyId())
                : ProjectResponseAssembler.Viewer.professional();

        return projectResponseAssembler.toDTO(p, viewer, companyDTO);
    }

    //  Utilitários de identidade 

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