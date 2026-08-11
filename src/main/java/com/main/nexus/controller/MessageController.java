package com.main.nexus.controller;

import com.main.nexus.dto.ChatSummaryDTO;
import com.main.nexus.dto.MessageDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.Message;
import com.main.nexus.model.Professional;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.MessageRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.service.ChatService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat")
public class MessageController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @GetMapping("/matches")
    public List<ChatSummaryDTO> listChats() {
        UserDTO logged = getLoggedUser();

        return getMatchedMatches(logged.id()).stream()
                .map(m -> toChatSummaryDTO(m, logged.id()))
                .sorted(Comparator.comparing(
                        ChatSummaryDTO::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @GetMapping("/unread-total")
    public ResponseEntity<Long> getUnreadTotal() {
        UserDTO logged = getLoggedUser();
        return ResponseEntity.ok(chatService.countUnreadTotalForUser(logged.id()));
    }

    // Matches confirmados do usuário logado

    private List<Match> getMatchedMatches(Long userId) {
        List<Match> matches;
        Optional<Professional> professional = professionalRepository.findByUserId(userId);
        if (professional.isPresent()) {
            matches = matchRepository.findByProfessionalId(professional.get().getId());
        } else {
            Company company = companyRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatusCode.valueOf(404), "User profile not found."));
            matches = matchRepository.findByProjectCompanyId(company.getId());
        }

        return matches.stream()
                .filter(m -> m.getStatus() == StatusMatch.MATCHED)
                .toList();
    }

    @GetMapping("/{matchId}/messages")
    @Transactional
    public List<MessageDTO> getMessages(@PathVariable Long matchId) {
        UserDTO logged = getLoggedUser();
        //Valida o acesso do usuario logados
        Match match = chatService.validateReadAccess(matchId, logged.id());

        // nao marcar mensagens como lidas em um chat encerrado
        if (Boolean.TRUE.equals(match.getActive())) {
            // Caso esteja ativo, todas as mensagens que nao foram lidas, e marcado como lidas
            messageRepository.markAllAsReadInMatch(matchId, logged.id());
        }

        return messageRepository.findByMatchIdOrderBySentAtAsc(matchId).stream()
                .map(message -> toMessageDTO(message, match))
                .toList();
    }

    @GetMapping("/{matchId}/unread-count")
    public ResponseEntity<Long> getUnreadCount(@PathVariable Long matchId) {
        UserDTO logged = getLoggedUser();
        chatService.validateChatAccess(matchId, logged.id());

        long count = messageRepository.countByMatchIdAndReadFalseAndSenderIdNot(matchId, logged.id());
        return ResponseEntity.ok(count);
    }

    // conversão para DTO 
    // Monta um resumo de cada conversa para a lista de chats
    private ChatSummaryDTO toChatSummaryDTO(Match match, Long myUserId) {
        boolean iAmProfessional = myUserId.equals(match.getProfessional().getUser().getId());

        String otherPartyName;
        String otherPartyPhotoUrl;
        String otherPartyType;
        if (iAmProfessional) {
            Company company = match.getProject().getCompany();
            otherPartyName = company.getCompanyName();
            otherPartyPhotoUrl = company.getProfilePhotoUrl();
            otherPartyType = "COMPANY";
        } else {
            Professional prof = match.getProfessional();
            otherPartyName = prof.getName();
            otherPartyPhotoUrl = prof.getProfilePhotoUrl();
            otherPartyType = "PROFESSIONAL";
        }

        // Pega todas as mensagens e organiza em ordem cronologica
        List<Message> messages = messageRepository.findByMatchIdOrderBySentAtAsc(match.getId());
        //Pega a ultima mensagem para mostrar no card no frontend
        Message lastMessage = messages.isEmpty() ? null : messages.get(messages.size() - 1);

        // Pega todas as mensagens nao lidas, mas filtra as que nao foram mandadas pelo usuario
        long unreadCount = messageRepository.countByMatchIdAndReadFalseAndSenderIdNot(match.getId(), myUserId);

        long daysUntilExpiration;
        if (Boolean.FALSE.equals(match.getActive())) {
            // Mcalcula quantos dias faltam para o match completar 30 dias, 
            // com -1 sinalizando "já expirado" e o resultado normal limitado entre 0 e 30. 
            // alimenta um indicador visual no frontend tipo "expira em 12 dias" na lista de conversas.
            daysUntilExpiration = -1;
        } else {
            daysUntilExpiration = ChronoUnit.DAYS.between( // Calcula quantos dias faltam para chegar, de hoje ate a data de criacao mais 30 dias
                    LocalDate.now(), 
                    match.getCreatedAt().toLocalDate().plusDays(30)); // Pega a data que o match foi criado e adiciona 30 dias
            daysUntilExpiration = Math.max(0, Math.min(30, daysUntilExpiration)); // Nunca deixe ser menor que 0 e nunca deixe ser maior que 30
            // Se for maior que 30 vai ser 30. Se for menor que 0, vai ser 0 
        }

        return new ChatSummaryDTO(
                match.getId(),
                otherPartyName,
                otherPartyPhotoUrl,
                otherPartyType,
                match.getProject().getTitle(),
                lastMessage != null ? lastMessage.getContent() : null, //Conteudo da ultima mensagm
                lastMessage != null ? lastMessage.getSentAt() : null, // horario que foi enviada
                unreadCount,
                match.getActive(),
                (int) daysUntilExpiration);
    }

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

    // Utilitários de identidade

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
