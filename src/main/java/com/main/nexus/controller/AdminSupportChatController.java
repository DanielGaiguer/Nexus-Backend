package com.main.nexus.controller;

import com.main.nexus.dto.OpenSupportConversationRequestDTO;
import com.main.nexus.dto.SupportConversationDTO;
import com.main.nexus.dto.SupportMessageDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.service.SupportChatService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Chat de suporte -- lado do Admin (abre, conversa, fecha). Protegido pela regra
// genérica /api/admin/** (hasRole("ADMIN")) em SecurityConfig.
@RestController
@RequestMapping("/api/admin/support")
public class AdminSupportChatController {

    @Autowired
    private SupportChatService supportChatService;

    @GetMapping("/conversations")
    public ResponseEntity<List<SupportConversationDTO>> list(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(supportChatService.listForAdmin(status));
    }

    @PostMapping("/conversations")
    public ResponseEntity<SupportConversationDTO> open(
            @RequestBody OpenSupportConversationRequestDTO body) {
        return ResponseEntity.ok(supportChatService.open(
                loggedUserId(), body.userId(), body.subject(), body.message()));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<SupportConversationDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(supportChatService.getForAdmin(id));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<SupportMessageDTO>> messages(@PathVariable Long id) {
        return ResponseEntity.ok(supportChatService.messagesForAdmin(id, loggedUserId()));
    }

    @PostMapping("/conversations/{id}/close")
    public ResponseEntity<SupportConversationDTO> close(@PathVariable Long id) {
        return ResponseEntity.ok(supportChatService.close(loggedUserId(), id));
    }

    @GetMapping("/unread-total")
    public ResponseEntity<Long> unreadTotal() {
        return ResponseEntity.ok(supportChatService.unreadTotalForAdmin());
    }

    private Long loggedUserId() {
        UserDTO logged = (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return logged.id();
    }
}
