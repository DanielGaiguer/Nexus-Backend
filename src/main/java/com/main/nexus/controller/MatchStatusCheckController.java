package com.main.nexus.controller;

import com.main.nexus.dto.MatchStatusCheckRequestDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.MatchStatusCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/matches")
public class MatchStatusCheckController {

    @Autowired
    private MatchStatusCheckService matchStatusCheckService;

    @Autowired
    private CompanyService companyService;

    @PostMapping("/{matchId}/status-check")
    public ResponseEntity<String> answerStatusCheck(
            @PathVariable Long matchId,
            @RequestBody MatchStatusCheckRequestDTO request) {
        Long companyId = getLoggedCompanyId();
        matchStatusCheckService.answerStatusCheck(matchId, companyId, request.outcome());
        return ResponseEntity.ok("Status check answered.");
    }

    // Utilitários de identidade 

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
}
