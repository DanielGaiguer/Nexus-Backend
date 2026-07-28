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

    List<Message> findByMatchIdOrderBySentAtAsc(Long matchId);

    List<Message> findByMatchIdAndReadFalseAndSenderIdNot(Long matchId, Long senderId);

    long countByMatchIdAndReadFalseAndSenderIdNot(Long matchId, Long senderId);

    @Modifying
    @Query("UPDATE Message m SET m.read = true WHERE m.match.id = :matchId AND m.sender.id != :userId AND m.read = false")
    void markAllAsReadInMatch(@Param("matchId") Long matchId, @Param("userId") Long userId);
}
