package com.main.nexus.repository;

import com.main.nexus.model.SupportMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    List<SupportMessage> findByConversationIdOrderBySentAtAsc(Long conversationId);

    long countByConversationIdAndReadFalseAndSenderIdNot(Long conversationId, Long senderId);

    // Nao-lidas do lado do Admin numa conversa: mensagens enviadas pelo usuario
    // (dono da conversa) ainda nao lidas -- nao depende de qual admin, igual a
    // countUnreadTotalForAdmin.
    long countByConversationIdAndReadFalseAndSenderId(Long conversationId, Long senderId);

    // Total de nao-lidas do usuario em todas as conversas de suporte dele
    // (independente de OPEN/CLOSED -- historico ainda pode ter nao-lidas).
    @Query("SELECT COUNT(m) FROM SupportMessage m "
         + "WHERE m.read = false AND m.sender.id <> :userId "
         + "AND m.conversation.user.id = :userId")
    long countUnreadTotalForUser(@Param("userId") Long userId);

    // Total de nao-lidas do lado do Admin (mensagens enviadas pelos usuarios,
    // em conversas OPEN).
    @Query("SELECT COUNT(m) FROM SupportMessage m "
         + "WHERE m.read = false "
         + "AND m.sender.id = m.conversation.user.id "
         + "AND m.conversation.status = com.main.nexus.model.enums.SupportConversationStatus.OPEN")
    long countUnreadTotalForAdmin();

    @Modifying
    @Query("UPDATE SupportMessage m SET m.read = true "
         + "WHERE m.conversation.id = :conversationId AND m.sender.id <> :userId AND m.read = false")
    void markAllAsRead(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
}
