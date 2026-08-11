package com.main.nexus.controller;

import com.main.nexus.dto.MessageDTO;
import com.main.nexus.dto.SendMessageRequestDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.Message;
import com.main.nexus.model.Professional;
import com.main.nexus.model.User;
import com.main.nexus.repository.MessageRepository;
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

        // O Principal representa quem está autenticado na conexão/requisição.
        
        Long userId = ((User) ((UsernamePasswordAuthenticationToken) principal)
                .getPrincipal()).getId();
        
        // é diferente do que o JwtFilter faz nas requisições REST comuns, onde o principal é um UserDTO (visto em todo getLoggedUser() dos controllers REST).
        // Ou seja, o mesmo conceito de "usuário autenticado" é representado por dois tipos Java diferentes, dependendo se a requisição chegou via HTTP REST (UserDTO) 
        // ou via WebSocket (User entidade completa)

        try {
            // valida se aquele usuario pode acessar o chat
            Match match = chatService.validateChatAccess(matchId, userId);

            // Valida tamanho da mensagem
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

            // broadcast: qualquer cliente inscrito nesse tópico recebe a mensagem em tempo real. Como é um /topic (broadcast),
            // tanto o remetente quanto o destinatário (se ambos estiverem com a tela do chat aberta e inscritos) recebem a mesma publicação
            // é assim que a mensagem aparece instantaneamente na tela de quem está no chat.
            simpMessagingTemplate.convertAndSend("/topic/chat/" + matchId, messageDTO);
            
            // Mas se o destinatário não estiver com aquele chat aberto (tá em outra página do sistema, ou só logado sem estar na tela de chat),
            // ele não está inscrito em /topic/chat/{matchId},  então não recebe nada por esse canal. É aí que entra o outro

            User otherParty = chatService.getOtherParty(match, userId);
            long unreadTotal = chatService.countUnreadTotalForUser(otherParty.getId());
            
            simpMessagingTemplate.convertAndSendToUser(
                    otherParty.getId().toString(),
                    "/queue/chat-notification",
                    Map.of("matchId", matchId, "unreadCount", unreadTotal));
            //  não é o conteúdo da mensagem, é só um sinal leve de "você tem mensagem nova nesse match". Serve pra atualizar um badge/contador de não-lidas na UI
        } catch (Exception e) {
            // genérico, publicando em /user/queue/errors: qualquer falha durante todo o processo (match inválido, conteúdo vazio, erro de banco) é capturada 
            // de forma ampla e reenviada só ao remetente via fila pessoal de erros — o cliente STOMP (Fluxo 18) escuta esse destino para exibir mensagens de 
            // erro na UI sem quebrar a conexão WebSocket inteira. É um padrão de tratamento de erro adequado para STOMP, já que uma exceção não tratada aqui não
            // vira automaticamente uma resposta HTTP como aconteceria numa API REST — precisa ser publicada manualmente de volta ao cliente por um destino dedicado
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
