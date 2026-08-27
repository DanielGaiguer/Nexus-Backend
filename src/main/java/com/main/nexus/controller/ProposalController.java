package com.main.nexus.controller;

import com.main.nexus.dto.ProposalRequestDTO;
import com.main.nexus.dto.ProposalResponseDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Match;
import com.main.nexus.model.Proposal;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.ProfessionalService;
import com.main.nexus.service.ProposalService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/proposals")
public class ProposalController {

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private ProfessionalService professionalService;

    @Autowired
    private CompanyService companyService;

    // PROFISSIONAL — envio, edição, retirada, anexos

    @PostMapping
    public ResponseEntity<ProposalResponseDTO> submit(@RequestBody ProposalRequestDTO request) {
        Proposal proposal = proposalService.submitProposal(request, getLoggedProfessionalId());
        return ResponseEntity.ok(proposalService.toResponseDTO(proposal));
    }

    @PutMapping("/{proposalId}")
    public ResponseEntity<ProposalResponseDTO> update(
            @PathVariable Long proposalId, @RequestBody ProposalRequestDTO request) {
        Proposal proposal = proposalService.updateProposal(proposalId, request, getLoggedProfessionalId());
        return ResponseEntity.ok(proposalService.toResponseDTO(proposal));
    }

    @PostMapping("/{proposalId}/withdraw")
    public ResponseEntity<String> withdraw(@PathVariable Long proposalId) {
        proposalService.withdrawProposal(proposalId, getLoggedProfessionalId());
        return ResponseEntity.ok("Proposal withdrawn.");
    }

    @PostMapping("/{proposalId}/attachments")
    public ResponseEntity<ProposalResponseDTO> addAttachments(
            @PathVariable Long proposalId, @RequestParam("files") List<MultipartFile> files) {
        Proposal proposal = proposalService.addAttachments(proposalId, getLoggedProfessionalId(), files);
        return ResponseEntity.ok(proposalService.toResponseDTO(proposal));
    }

    @DeleteMapping("/{proposalId}/attachments/{attachmentId}")
    public ResponseEntity<String> removeAttachment(
            @PathVariable Long proposalId, @PathVariable Long attachmentId) {
        proposalService.removeAttachment(proposalId, attachmentId, getLoggedProfessionalId());
        return ResponseEntity.ok("Attachment removed.");
    }

    @GetMapping("/mine")
    public ResponseEntity<List<ProposalResponseDTO>> mine() {
        List<Proposal> proposals = proposalService.listProposalsForProfessional(getLoggedProfessionalId());
        return ResponseEntity.ok(proposals.stream().map(proposalService::toResponseDTO).toList());
    }

    // EMPRESA — listagem por projeto, listagem geral, aceite, recusa

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<ProposalResponseDTO>> forProject(@PathVariable Long projectId) {
        List<Proposal> proposals = proposalService.listProposalsForProject(projectId, getLoggedCompanyId());
        return ResponseEntity.ok(proposals.stream().map(proposalService::toResponseDTO).toList());
    }

    @GetMapping("/company/mine")
    public ResponseEntity<List<ProposalResponseDTO>> forCompany() {
        List<Proposal> proposals = proposalService.listProposalsForCompany(getLoggedCompanyId());
        return ResponseEntity.ok(proposals.stream().map(proposalService::toResponseDTO).toList());
    }

    @PostMapping("/{proposalId}/accept")
    public ResponseEntity<String> accept(@PathVariable Long proposalId) {
        Match match = proposalService.acceptProposal(proposalId, getLoggedCompanyId());
        return ResponseEntity.ok("Proposal accepted! Match confirmed: " + match.getId());
    }

    @PostMapping("/{proposalId}/reject")
    public ResponseEntity<String> reject(@PathVariable Long proposalId) {
        proposalService.rejectProposal(proposalId, getLoggedCompanyId());
        return ResponseEntity.ok("Proposal rejected.");
    }

    // COMPARTILHADO — detalhe, visível pro participante (dono do projeto ou autor da proposta)

    @GetMapping("/{proposalId}")
    public ResponseEntity<ProposalResponseDTO> findById(@PathVariable Long proposalId) {
        UserDTO logged = getLoggedUser();
        Long companyId = "COMPANY".equals(logged.role()) ? getLoggedCompanyId() : null;
        Long professionalId = "PROFESSIONAL".equals(logged.role()) ? getLoggedProfessionalId() : null;

        Proposal proposal = proposalService.getForParticipant(proposalId, companyId, professionalId);
        return ResponseEntity.ok(proposalService.toResponseDTO(proposal));
    }

    // UTILITÁRIOS DE IDENTIDADE — mesmo padrão de MatchController

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
