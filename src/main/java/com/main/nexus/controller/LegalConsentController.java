package com.main.nexus.controller;

import com.main.nexus.dto.ConsentStatusDTO;
import com.main.nexus.dto.ReacceptConsentDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.service.UserConsentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Estado de consentimento do usuario logado + re-aceite. Autenticado
// (/api/legal/** -> .authenticated() em SecurityConfig). Fica FORA da checagem
// do ConsentGateFilter de proposito -- e por aqui que o usuario retido se
// desbloqueia.
@RestController
@RequestMapping("/api/legal/consent")
public class LegalConsentController {

    @Autowired
    private UserConsentService userConsentService;

    @GetMapping("/status")
    public ResponseEntity<ConsentStatusDTO> status() {
        return ResponseEntity.ok(userConsentService.status(loggedUserId()));
    }

    @PostMapping("/reaccept")
    public ResponseEntity<ConsentStatusDTO> reaccept(@RequestBody ReacceptConsentDTO body) {
        Long userId = loggedUserId();
        userConsentService.reaccept(
                userId,
                body.acceptedTermsOfUse(),
                body.acceptedMarketingCommunications(),
                body.acceptedAlgorithmImprovement());
        return ResponseEntity.ok(userConsentService.status(userId));
    }

    private Long loggedUserId() {
        UserDTO logged = (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return logged.id();
    }
}
