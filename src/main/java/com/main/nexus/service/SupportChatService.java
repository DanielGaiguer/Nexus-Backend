package com.main.nexus.service;

import com.main.nexus.dto.SupportConversationDTO;
import com.main.nexus.dto.SupportMessageDTO;
import com.main.nexus.model.SupportConversation;
import com.main.nexus.model.SupportMessage;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.SupportConversationStatus;
import com.main.nexus.model.enums.UserType;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.SupportConversationRepository;
import com.main.nexus.repository.SupportMessageRepository;
import com.main.nexus.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Chat de suporte Admin <-> usuario. Separado do chat de match (ChatService /
// tb_message): o chamado e aberto pelo Admin (open) ou pelo proprio usuario
// (openByUser); so o Admin fecha; ambos os lados respondem enquanto OPEN.
// Transporte reaproveitado (STOMP /ws, mesmo WebSocketAuthInterceptor) --
// ver SupportChatWebSocketHandler.
@Service
public class SupportChatService {

    private static final int MAX_MESSAGE_LEN = 2000;

    @Autowired
    private SupportConversationRepository conversationRepository;

    @Autowired
    private SupportMessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private NotificationService notificationService;

    // ─── Abertura / fechamento (Admin) ─────────────────────────────

    @Transactional
    public SupportConversationDTO open(Long adminUserId, Long targetUserId,
                                      String subject, String message) {
        if (targetUserId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required.");
        }
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found."));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found: " + targetUserId));
        if (target.getType() == UserType.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Support conversations are only with professionals or contractors.");
        }

        // Idempotente: se ja existe uma conversa OPEN com esse usuario, reaproveita.
        SupportConversation conversation = conversationRepository
                .findFirstByUserIdAndStatus(targetUserId, SupportConversationStatus.OPEN)
                .orElse(null);

        boolean isNew = conversation == null;
        if (isNew) {
            conversation = new SupportConversation();
            conversation.setUser(target);
            conversation.setOpenedByAdmin(admin);
            conversation.setSubject(clean(subject));
            conversation.setStatus(SupportConversationStatus.OPEN);
            conversation.setCreatedAt(LocalDateTime.now());
            conversation = conversationRepository.save(conversation);
        }

        if (message != null && !message.isBlank()) {
            persistMessage(conversation, admin, message);
        }

        if (isNew) {
            notificationService.notifySupportConversationOpened(
                    target, conversation.getSubject(), conversation.getId());
        }

        return toConversationDTO(conversation, targetUserId /* unread irrelevante aqui */, false);
    }

    // Abertura pelo proprio usuario (profissional/contratante). A conversa nasce
    // sem admin dono -- o primeiro admin a responder assume (ver recordMessage).
    @Transactional
    public SupportConversationDTO openByUser(Long userId, String subject, String message) {
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Descreva o motivo do contato para abrir um chamado.");
        }
        if (message.length() > MAX_MESSAGE_LEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A mensagem não pode passar de " + MAX_MESSAGE_LEN + " caracteres.");
        }
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        if (requester.getType() == UserType.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Chamados de suporte são abertos por profissionais ou contratantes.");
        }

        // Um chamado OPEN por usuario. Se ja existe (aberto por ele ou pelo Admin),
        // a mensagem entra nesse thread -- nao cria outro.
        SupportConversation conversation = conversationRepository
                .findFirstByUserIdAndStatus(userId, SupportConversationStatus.OPEN)
                .orElse(null);
        boolean isNew = conversation == null;
        if (isNew) {
            conversation = new SupportConversation();
            conversation.setUser(requester);
            conversation.setOpenedByUser(true);
            conversation.setSubject(clean(subject));
            conversation.setStatus(SupportConversationStatus.OPEN);
            conversation.setCreatedAt(LocalDateTime.now());
            conversation = conversationRepository.save(conversation);
        }

        persistMessage(conversation, requester, message);

        if (isNew) {
            String who = resolveDisplay(requester).name();
            String subj = conversation.getSubject();
            Long convId = conversation.getId();
            for (User admin : userRepository.findByType(UserType.ADMIN)) {
                notificationService.notifySupportConversationRequested(admin, who, subj, convId);
            }
        }

        return toConversationDTO(conversation, userId, false);
    }

    @Transactional
    public SupportConversationDTO close(Long adminUserId, Long conversationId) {
        SupportConversation conversation = conversationById(conversationId);
        if (conversation.getStatus() == SupportConversationStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta conversa de suporte já está fechada.");
        }
        conversation.setStatus(SupportConversationStatus.CLOSED);
        conversation.setClosedAt(LocalDateTime.now());
        userRepository.findById(adminUserId).ifPresent(conversation::setClosedByAdmin);
        return toConversationDTO(conversationRepository.save(conversation), null, true);
    }

    // ─── Listagens ────────────────────────────────────────────────

    public List<SupportConversationDTO> listForAdmin(String status) {
        List<SupportConversation> rows;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            rows = conversationRepository.findAllByOrderByCreatedAtDesc();
        } else {
            rows = conversationRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status));
        }
        return rows.stream()
                .map(c -> toConversationDTO(c, null, true))
                .sorted(Comparator.comparing(SupportConversationDTO::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<SupportConversationDTO> listForUser(Long userId) {
        return conversationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(c -> toConversationDTO(c, userId, false))
                .sorted(Comparator.comparing(SupportConversationDTO::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public SupportConversationDTO getForAdmin(Long conversationId) {
        SupportConversation c = conversationById(conversationId);
        return toConversationDTO(c, null, true);
    }

    public SupportConversationDTO getForUser(Long conversationId, Long userId) {
        SupportConversation c = conversationById(conversationId);
        assertIsTheUser(c, userId);
        return toConversationDTO(c, userId, false);
    }

    // ─── Histórico ────────────────────────────────────────────────

    @Transactional
    public List<SupportMessageDTO> messagesForAdmin(Long conversationId, Long adminUserId) {
        conversationById(conversationId); // 404 se não existir
        messageRepository.markAllAsRead(conversationId, adminUserId);
        return messageRepository.findByConversationIdOrderBySentAtAsc(conversationId).stream()
                .map(this::toMessageDTO).toList();
    }

    @Transactional
    public List<SupportMessageDTO> messagesForUser(Long conversationId, Long userId) {
        SupportConversation c = conversationById(conversationId);
        assertIsTheUser(c, userId);
        messageRepository.markAllAsRead(conversationId, userId);
        return messageRepository.findByConversationIdOrderBySentAtAsc(conversationId).stream()
                .map(this::toMessageDTO).toList();
    }

    public long unreadTotalForUser(Long userId) {
        return messageRepository.countUnreadTotalForUser(userId);
    }

    public long unreadTotalForAdmin() {
        return messageRepository.countUnreadTotalForAdmin();
    }

    // ─── Envio (usado pelo SupportChatWebSocketHandler) ───────────

    // Valida acesso + estado, persiste e devolve o DTO pronto para o broadcast.
    @Transactional
    public SupportMessageDTO recordMessage(Long conversationId, Long senderUserId, String content) {
        SupportConversation conversation = conversationById(conversationId);
        User sender = userRepository.findById(senderUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        boolean isAdmin = sender.getType() == UserType.ADMIN;
        boolean isTheUser = conversation.getUser().getId().equals(senderUserId);
        if (!isAdmin && !isTheUser) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not part of this support conversation.");
        }
        if (conversation.getStatus() != SupportConversationStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.valueOf(410),
                    "Esta conversa de suporte foi fechada.");
        }
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content must not be empty.");
        }
        if (content.length() > MAX_MESSAGE_LEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Message content must not exceed " + MAX_MESSAGE_LEN + " characters.");
        }

        // Chamado aberto pelo usuario: o primeiro admin a responder vira o dono
        // da conversa (restaura o "nudge" por WebSocket para as proximas mensagens).
        if (isAdmin && conversation.getOpenedByAdmin() == null) {
            conversation.setOpenedByAdmin(sender);
            conversationRepository.save(conversation);
        }

        return toMessageDTO(persistMessage(conversation, sender, content));
    }

    // Quem deve receber o sinal de "nova mensagem" — o outro lado da conversa.
    // Retorna [userId, isRecipientAdmin].
    public Recipient recipientOf(Long conversationId, Long senderUserId) {
        SupportConversation c = conversationById(conversationId);
        boolean senderIsTheUser = c.getUser().getId().equals(senderUserId);
        if (senderIsTheUser) {
            // Chamado aberto pelo usuario e ainda sem admin dono: nao ha um
            // destinatario unico -- o handler pula o nudge (a lista do Admin faz polling).
            User admin = c.getOpenedByAdmin();
            return new Recipient(admin != null ? admin.getId() : null, true);
        }
        return new Recipient(c.getUser().getId(), false);
    }

    public record Recipient(Long userId, boolean isAdmin) {}

    // ─── Internos ────────────────────────────────────────────────

    private SupportMessage persistMessage(SupportConversation conversation, User sender, String content) {
        SupportMessage m = new SupportMessage();
        m.setConversation(conversation);
        m.setSender(sender);
        m.setContent(content.trim());
        m.setSentAt(LocalDateTime.now());
        m.setRead(false);
        return messageRepository.save(m);
    }

    private SupportConversation conversationById(Long id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Support conversation not found: " + id));
    }

    private void assertIsTheUser(SupportConversation c, Long userId) {
        if (!c.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This support conversation does not belong to you.");
        }
    }

    private SupportConversationStatus parseStatus(String status) {
        try {
            return SupportConversationStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + status);
        }
    }

    private String clean(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    // adminView=true: unreadCount = mensagens do usuario ainda nao lidas (independe
    // de qual admin). adminView=false: usa unreadForUserId (mensagens que nao sao
    // dele) -- null zera a contagem.
    private SupportConversationDTO toConversationDTO(SupportConversation c, Long unreadForUserId,
                                                    boolean adminView) {
        List<SupportMessage> messages = messageRepository
                .findByConversationIdOrderBySentAtAsc(c.getId());
        SupportMessage last = messages.isEmpty() ? null : messages.get(messages.size() - 1);

        long unread;
        if (adminView) {
            // Lado do Admin: nao-lidas = mensagens do usuario (dono) ainda nao lidas.
            // Independe de qual admin -- mesmo criterio de countUnreadTotalForAdmin.
            unread = messageRepository.countByConversationIdAndReadFalseAndSenderId(
                    c.getId(), c.getUser().getId());
        } else {
            unread = unreadForUserId == null ? 0
                    : messageRepository.countByConversationIdAndReadFalseAndSenderIdNot(
                            c.getId(), unreadForUserId);
        }

        Display d = resolveDisplay(c.getUser());

        return new SupportConversationDTO(
                c.getId(),
                c.getUser().getId(),
                d.name(),
                d.role(),
                d.photoUrl(),
                c.getSubject(),
                c.getStatus().name(),
                c.getOpenedByAdmin() != null ? c.getOpenedByAdmin().getEmail() : null,
                c.isOpenedByUser(),
                c.getCreatedAt(),
                c.getClosedAt(),
                last != null ? last.getContent() : null,
                last != null ? last.getSentAt() : null,
                unread);
    }

    private SupportMessageDTO toMessageDTO(SupportMessage m) {
        User sender = m.getSender();
        String role;
        String name;
        String photo;
        if (sender.getType() == UserType.ADMIN) {
            role = "ADMIN";
            name = "Suporte Nexus";
            photo = null;
        } else {
            Display d = resolveDisplay(sender);
            role = d.role();
            name = d.name();
            photo = d.photoUrl();
        }
        return new SupportMessageDTO(
                m.getId(),
                m.getConversation().getId(),
                sender.getId(),
                name,
                role,
                photo,
                m.getContent(),
                m.getSentAt(),
                m.getRead());
    }

    private record Display(String name, String role, String photoUrl) {}

    // Nome/role/foto de um usuario nao-admin — mesmo criterio do AdminController.
    private Display resolveDisplay(User u) {
        return professionalRepository.findByUserId(u.getId())
                .map(p -> new Display(p.getName(), "PROFESSIONAL", p.getProfilePhotoUrl()))
                .or(() -> companyRepository.findByUserId(u.getId())
                        .map(co -> new Display(co.getCompanyName(), "COMPANY", co.getProfilePhotoUrl())))
                .orElse(new Display(u.getEmail(), u.getType().name(), null));
    }
}
