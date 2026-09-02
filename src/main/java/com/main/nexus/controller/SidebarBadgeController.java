package com.main.nexus.controller;

import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.enums.SidebarSection;
import com.main.nexus.service.SidebarBadgeService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Badges da sidebar do usuário logado. Vale para os 3 papéis -- o service decide
// o que responder a partir do papel. Cai na regra genérica
// .anyRequest().authenticated() do SecurityConfig.
@RestController
@RequestMapping("/api/sidebar")
public class SidebarBadgeController {

    @Autowired
    private SidebarBadgeService sidebarBadgeService;

    // href do item da sidebar -> contagem (só itens com contagem > 0).
    // Recalculado a cada chamada -- o frontend chama no carregamento da sidebar.
    @GetMapping("/badges")
    public ResponseEntity<Map<String, Long>> badges() {
        return ResponseEntity.ok(sidebarBadgeService.badgesFor(loggedUser()));
    }

    // O usuário abriu uma seção do "Padrão B" -- registra o "visto" e zera o badge.
    @PostMapping("/sections/{section}/seen")
    public ResponseEntity<Void> markSeen(@PathVariable String section) {
        SidebarSection parsed;
        try {
            parsed = SidebarSection.valueOf(section);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown section: " + section);
        }
        sidebarBadgeService.markSeen(loggedUser().id(), parsed);
        return ResponseEntity.noContent().build();
    }

    private UserDTO loggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
