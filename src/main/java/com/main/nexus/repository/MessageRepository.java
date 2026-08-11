package com.main.nexus.repository;

import com.main.nexus.model.Message;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // Busca as mensagens pelo id do match e ordena em ordem cronologica
    List<Message> findByMatchIdOrderBySentAtAsc(Long matchId);

    // uma mensagem só conta como "não lida por mim" se eu não fui quem a enviou, porque sem esse filtro contariam/marcariam as próprias 
    // mensagens que a pessoa mandou (que nascem com read = false por padrão) como se fossem pendências dela mesma
    List<Message> findByMatchIdAndReadFalseAndSenderIdNot(Long matchId, Long senderId);

    long countByMatchIdAndReadFalseAndSenderIdNot(Long matchId, Long senderId);

    // Total de não lidas do usuário em todos os matches ativos e confirmados dele 
    @Query("SELECT COUNT(m) FROM Message m WHERE m.read = false AND m.sender.id != :userId " +
           "AND m.match.active = true " +
           "AND m.match.status = com.main.nexus.model.enums.StatusMatch.MATCHED " +
           "AND (m.match.professional.user.id = :userId OR m.match.project.company.user.id = :userId)")
    long countUnreadTotalForUser(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Message m SET m.read = true WHERE m.match.id = :matchId AND m.sender.id != :userId AND m.read = false")
    void markAllAsReadInMatch(@Param("matchId") Long matchId, @Param("userId") Long userId);
}
