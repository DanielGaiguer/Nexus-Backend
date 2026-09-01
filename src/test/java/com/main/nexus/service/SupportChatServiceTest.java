package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupportChatServiceTest {

    @Mock private SupportConversationRepository conversationRepository;
    @Mock private SupportMessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProfessionalRepository professionalRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private SupportChatService service;

    private User requester;

    @BeforeEach
    void setUp() {
        requester = new User();
        requester.setId(42L);
        requester.setEmail("empresa@example.com");
        requester.setType(UserType.COMPANY);
        when(userRepository.findById(42L)).thenReturn(Optional.of(requester));

        // resolveDisplay cai no fallback do e-mail.
        when(professionalRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
        when(companyRepository.findByUserId(anyLong())).thenReturn(Optional.empty());

        // toConversationDTO
        when(messageRepository.findByConversationIdOrderBySentAtAsc(any())).thenReturn(List.of());
        when(messageRepository.countByConversationIdAndReadFalseAndSenderId(any(), any())).thenReturn(0L);
        when(messageRepository.countByConversationIdAndReadFalseAndSenderIdNot(any(), any())).thenReturn(0L);

        when(conversationRepository.save(any(SupportConversation.class)))
                .thenAnswer(inv -> {
                    SupportConversation c = inv.getArgument(0);
                    if (c.getId() == null) {
                        c.setId(100L);
                    }
                    return c;
                });
        when(messageRepository.save(any(SupportMessage.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void openByUser_createsConversation_persistsMessage_andNotifiesEachAdmin() {
        when(conversationRepository.findFirstByUserIdAndStatus(42L, SupportConversationStatus.OPEN))
                .thenReturn(Optional.empty());
        User admin1 = new User();
        admin1.setId(1L);
        admin1.setType(UserType.ADMIN);
        User admin2 = new User();
        admin2.setId(2L);
        admin2.setType(UserType.ADMIN);
        when(userRepository.findByType(UserType.ADMIN)).thenReturn(List.of(admin1, admin2));

        service.openByUser(42L, "  Dúvida no pagamento  ", "Minha cobrança falhou e não sei por quê.");

        ArgumentCaptor<SupportConversation> convCaptor =
                ArgumentCaptor.forClass(SupportConversation.class);
        verify(conversationRepository).save(convCaptor.capture());
        SupportConversation saved = convCaptor.getValue();
        assertTrue(saved.isOpenedByUser());
        assertEquals(SupportConversationStatus.OPEN, saved.getStatus());
        assertEquals("Dúvida no pagamento", saved.getSubject());
        assertEquals(42L, saved.getUser().getId());

        ArgumentCaptor<SupportMessage> msgCaptor = ArgumentCaptor.forClass(SupportMessage.class);
        verify(messageRepository).save(msgCaptor.capture());
        assertEquals("Minha cobrança falhou e não sei por quê.", msgCaptor.getValue().getContent());
        assertEquals(42L, msgCaptor.getValue().getSender().getId());

        verify(notificationService, times(2))
                .notifySupportConversationRequested(any(User.class), any(), eq("Dúvida no pagamento"), eq(100L));
    }

    @Test
    void openByUser_blankMessage_throwsBadRequest_andPersistsNothing() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.openByUser(42L, "assunto", "   "));
        assertEquals(400, ex.getStatusCode().value());
        verify(conversationRepository, never()).save(any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void openByUser_calledByAdmin_throwsForbidden() {
        requester.setType(UserType.ADMIN);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.openByUser(42L, null, "oi"));
        assertEquals(403, ex.getStatusCode().value());
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void openByUser_reusesExistingOpenConversation_andSkipsAdminNotification() {
        SupportConversation existing = new SupportConversation();
        existing.setId(77L);
        existing.setUser(requester);
        existing.setStatus(SupportConversationStatus.OPEN);
        when(conversationRepository.findFirstByUserIdAndStatus(42L, SupportConversationStatus.OPEN))
                .thenReturn(Optional.of(existing));

        service.openByUser(42L, "novo assunto", "mais uma mensagem");

        verify(conversationRepository, never()).save(any(SupportConversation.class));
        ArgumentCaptor<SupportMessage> msgCaptor = ArgumentCaptor.forClass(SupportMessage.class);
        verify(messageRepository).save(msgCaptor.capture());
        assertEquals(77L, msgCaptor.getValue().getConversation().getId());
        verify(notificationService, never())
                .notifySupportConversationRequested(any(), any(), any(), any());
    }
}
