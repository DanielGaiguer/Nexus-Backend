package com.main.nexus.controller;

import com.main.nexus.dto.CandidateComparisonRequestDTO;
import com.main.nexus.dto.CandidateComparisonResponseDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.service.CandidateComparisonService;
import com.main.nexus.service.CompanyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/comparison")
public class CandidateComparisonController {

    @Autowired
    private CandidateComparisonService comparisonService;

    @Autowired
    private CompanyService companyService;

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

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}