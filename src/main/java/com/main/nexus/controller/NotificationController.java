package com.main.nexus.controller;

import com.main.nexus.dto.NotificationDTO;
import com.main.nexus.dto.NotificationSummaryDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.User;
import com.main.nexus.repository.UserRepository;
import com.main.nexus.service.NotificationService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    // Resumo completo todas as notificações e contagem de nao lidas
    @GetMapping
    public ResponseEntity<NotificationSummaryDTO> getSummary() {
        User user = getLoggedUserEntity();
        return ResponseEntity.ok(notificationService.getSummary(user.getId()));
    }

    // so as nao lidas — usado para popular o badge do sino no primeiro load
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationDTO>> getUnread() {
        User user = getLoggedUserEntity();
        return ResponseEntity.ok(notificationService.getUnread(user.getId()));
    }

    // Contagem de não lidas endpoint leve para polling periódico do frontend
    @GetMapping("/unread/count")
    public ResponseEntity<Long> getUnreadCount() {
        User user = getLoggedUserEntity();
        return ResponseEntity.ok(notificationService.getUnreadCount(user.getId()));
    }

    // marca todas como lidas
    @PutMapping("/read-all")
    public ResponseEntity<String> markAllAsRead() {
        User user = getLoggedUserEntity();
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok("All notifications marked as read.");
    }

    // marca uma notificação específica como lida
    @PutMapping("/{id}/read")
    public ResponseEntity<String> markAsRead(@PathVariable Long id) {
        User user = getLoggedUserEntity();
        notificationService.markAsRead(id, user.getId());
        return ResponseEntity.ok("Notification marked as read.");
    }

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    private User getLoggedUserEntity() {
        UserDTO logged = getLoggedUser();
        return userRepository.findById(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "User not found."));
    }
}