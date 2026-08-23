package com.main.nexus.controller;

import com.main.nexus.dto.CandidateComparisonRequestDTO;
import com.main.nexus.dto.CandidateComparisonResponseDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Professional;
import com.main.nexus.service.CandidateComparisonService;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.ProfessionalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/comparison")
public class CandidateComparisonController {

    @Autowired
    private CandidateComparisonService comparisonService;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private ProfessionalService professionalService;

    @PostMapping("/candidates")
    public ResponseEntity<CandidateComparisonResponseDTO> compareCandidates(
            @RequestBody CandidateComparisonRequestDTO request) {

        UserDTO logged = getLoggedUser();
        Company company = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company profile not found."));

        return ResponseEntity.ok(
                comparisonService.compare(request, company.getId()));
    }

    // Mesma comparação, mas vista pelo próprio profissional — ele só pode
    // comparar consigo mesmo, com um match que já é dele (ver CandidateComparisonService#compareForProfessional).
    @PostMapping("/candidates/mine")
    public ResponseEntity<CandidateComparisonResponseDTO> compareMine(
            @RequestParam Long matchId) {

        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional profile not found."));

        return ResponseEntity.ok(
                comparisonService.compareForProfessional(matchId, professional.getId()));
    }

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}