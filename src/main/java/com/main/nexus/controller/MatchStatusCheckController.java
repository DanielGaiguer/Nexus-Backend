package com.main.nexus.controller;

import com.main.nexus.dto.MatchConfirmationDTO;
import com.main.nexus.dto.MatchStatusCheckRequestDTO;
import com.main.nexus.dto.PendingStatusCheckDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.service.MatchStatusCheckService;
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

// Janela de confirmação pós-contratação (Prompt 2). Agora atende os DOIS papéis
// (contratante e profissional) — o serviço resolve o lado a partir do papel logado.
@RestController
@RequestMapping("/api/matches")
public class MatchStatusCheckController {

    @Autowired
    private MatchStatusCheckService matchStatusCheckService;

    // Alimenta o dialog "confirmação pendente" no dashboard de quem está logado.
    @GetMapping("/status-check/pending")
    public ResponseEntity<PendingStatusCheckDTO> getPendingStatusCheck() {
        return matchStatusCheckService.findPendingConfirmationFor(getLoggedUser())
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "No pending confirmation."));
    }

    @PostMapping("/{matchId}/status-check")
    public ResponseEntity<MatchConfirmationDTO> answerStatusCheck(
            @PathVariable Long matchId,
            @RequestBody MatchStatusCheckRequestDTO request) {
        return ResponseEntity.ok(matchStatusCheckService.recordAnswer(
                matchId, getLoggedUser(), request.outcome(), request.finalAmount()));
    }

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
