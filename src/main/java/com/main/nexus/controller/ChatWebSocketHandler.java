package com.main.nexus.controller;

import com.main.nexus.dto.MessageDTO;
import com.main.nexus.dto.SendMessageRequestDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.Message;
import com.main.nexus.model.Professional;
import com.main.nexus.model.User;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.MessageRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.UserRepository;
import com.main.nexus.service.ChatService;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class ChatWebSocketHandler {

    @Autowired
    private ChatService chatService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private UserRepository userRepository;

    @MessageMapping("/chat/{matchId}/send")
    @Transactional
    public void sendMessage(
            @DestinationVariable Long matchId,
            @Payload SendMessageRequestDTO request,
            Principal principal) {

        Long userId = ((User) ((UsernamePasswordAuthenticationToken) principal)
                .getPrincipal()).getId();

        try {
            Match match = chatService.validateChatAccess(matchId, userId);

            if (request.content() == null || request.content().isBlank()) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Message content must not be empty.");
            }

            if (request.content().length() > 2000) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Message content must not exceed 2000 characters.");
            }

            Message message = new Message();
            message.setMatch(match);
            message.setSender(userRepository.findById(userId).get());
            message.setContent(request.content().trim());
            message.setSentAt(LocalDateTime.now());
            message.setRead(false);

            Message saved = messageRepository.save(message);

            MessageDTO messageDTO = toMessageDTO(saved, match);

            simpMessagingTemplate.convertAndSend("/topic/chat/" + matchId, messageDTO);

            User otherParty = chatService.getOtherParty(match, userId);
            simpMessagingTemplate.convertAndSendToUser(
                    otherParty.getId().toString(),
                    "/queue/chat-notification",
                    Map.of("matchId", matchId, "unreadCount", 1));
        } catch (Exception e) {
            simpMessagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/errors",
                    Map.of("error", e.getMessage()));
        }
    }

    // Conversão para DTO

    private MessageDTO toMessageDTO(Message message, Match match) {
        boolean senderIsProfessional = message.getSender().getId()
                .equals(match.getProfessional().getUser().getId());

        String senderName;
        String senderType;
        String senderPhotoUrl;
        if (senderIsProfessional) {
            Professional prof = match.getProfessional();
            senderName = prof.getName();
            senderType = "PROFESSIONAL";
            senderPhotoUrl = prof.getProfilePhotoUrl();
        } else {
            Company company = match.getProject().getCompany();
            senderName = company.getCompanyName();
            senderType = "COMPANY";
            senderPhotoUrl = company.getProfilePhotoUrl();
        }

        return new MessageDTO(
                message.getId(),
                match.getId(),
                message.getSender().getId(),
                senderName,
                senderType,
                senderPhotoUrl,
                message.getContent(),
                message.getSentAt(),
                message.getRead());
    }
}
