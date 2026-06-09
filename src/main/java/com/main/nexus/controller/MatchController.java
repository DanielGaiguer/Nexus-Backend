package com.main.nexus.controller;

import com.main.nexus.dto.UserDTO;
import com.main.nexus.service.MatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    @Autowired
    private MatchService matchService;

    @GetMapping("/{matchId}")
    public ResponseEntity<?> findById(@PathVariable Long matchId) {
        return ResponseEntity.ok(matchService.findById(matchId));
    }

    @PostMapping("/{matchId}/company-interest")
    public ResponseEntity<String> companyShowsInterest(@PathVariable Long matchId) {
        matchService.companyShowsInterest(matchId);
        return ResponseEntity.ok("Interest sent to professional.");
    }

    @PostMapping("/{matchId}/accept")
    public ResponseEntity<String> professionalAccepts(@PathVariable Long matchId) {
        matchService.professionalAccepts(matchId);
        return ResponseEntity.ok("Match confirmed! Contact details are now available.");
    }

    @PostMapping("/{matchId}/reject")
    public ResponseEntity<String> professionalRejects(
            @PathVariable Long matchId,
            @org.springframework.web.bind.annotation.RequestParam String reason) {
        matchService.professionalRejectsWithFeedback(matchId, reason);
        return ResponseEntity.ok("Invite rejected.");
    }

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}