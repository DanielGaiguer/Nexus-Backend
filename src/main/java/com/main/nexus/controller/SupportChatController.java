package com.main.nexus.controller;

import com.main.nexus.dto.OpenSupportTicketRequestDTO;
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
import org.springframework.web.bind.annotation.RestController;

// Chat de suporte -- lado do usuário (profissional/contratante): abre um chamado,
// lê e responde; não fecha (só o Admin). Regra /api/support/** = authenticated()
// em SecurityConfig; o service valida que quem chama é o dono da conversa.
@RestController
@RequestMapping("/api/support")
public class SupportChatController {

    @Autowired
    private SupportChatService supportChatService;

    @GetMapping("/conversations")
    public ResponseEntity<List<SupportConversationDTO>> list() {
        return ResponseEntity.ok(supportChatService.listForUser(loggedUserId()));
    }

    // O usuário abre um chamado de suporte. Idempotente: se já há um chamado OPEN
    // dele, a mensagem entra nesse thread.
    @PostMapping("/conversations")
    public ResponseEntity<SupportConversationDTO> open(
            @RequestBody OpenSupportTicketRequestDTO body) {
        return ResponseEntity.ok(supportChatService.openByUser(
                loggedUserId(), body.subject(), body.message()));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<SupportConversationDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(supportChatService.getForUser(id, loggedUserId()));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<SupportMessageDTO>> messages(@PathVariable Long id) {
        return ResponseEntity.ok(supportChatService.messagesForUser(id, loggedUserId()));
    }

    @GetMapping("/unread-total")
    public ResponseEntity<Long> unreadTotal() {
        return ResponseEntity.ok(supportChatService.unreadTotalForUser(loggedUserId()));
    }

    private Long loggedUserId() {
        UserDTO logged = (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return logged.id();
    }
}
