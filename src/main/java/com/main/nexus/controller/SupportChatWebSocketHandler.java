package com.main.nexus.controller;

import com.main.nexus.dto.SendMessageRequestDTO;
import com.main.nexus.dto.SupportMessageDTO;
import com.main.nexus.model.User;
import com.main.nexus.service.SupportChatService;
import java.security.Principal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

// STOMP do chat de suporte. Mesmo transporte do chat de match (endpoint /ws,
// WebSocketAuthInterceptor autentica o CONNECT) -- só o destino muda:
//   envio:     /app/support/{conversationId}/send
//   broadcast: /topic/support/{conversationId}
//   sinal:     /user/{id}/queue/support-notification
@Controller
public class SupportChatWebSocketHandler {

    @Autowired
    private SupportChatService supportChatService;

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @MessageMapping("/support/{conversationId}/send")
    @Transactional
    public void sendMessage(
            @DestinationVariable Long conversationId,
            @Payload SendMessageRequestDTO request,
            Principal principal) {

        Long userId = ((User) ((UsernamePasswordAuthenticationToken) principal)
                .getPrincipal()).getId();

        try {
            SupportMessageDTO dto = supportChatService.recordMessage(
                    conversationId, userId, request.content());

            simpMessagingTemplate.convertAndSend("/topic/support/" + conversationId, dto);

            SupportChatService.Recipient recipient =
                    supportChatService.recipientOf(conversationId, userId);
            // recipient.userId() nulo = chamado aberto pelo usuário e ainda sem
            // admin dono: sem destinatário único p/ o nudge (a lista do Admin faz polling).
            if (recipient.userId() != null) {
                long unread = recipient.isAdmin()
                        ? supportChatService.unreadTotalForAdmin()
                        : supportChatService.unreadTotalForUser(recipient.userId());

                simpMessagingTemplate.convertAndSendToUser(
                        recipient.userId().toString(),
                        "/queue/support-notification",
                        Map.of("conversationId", conversationId, "unreadCount", unread));
            }
        } catch (Exception e) {
            simpMessagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/errors",
                    Map.of("error", e.getMessage() != null ? e.getMessage() : "Erro no chat de suporte."));
        }
    }
}
