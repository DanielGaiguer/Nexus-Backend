package com.main.nexus.repository;

import com.main.nexus.model.MatchHistory;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchHistoryRepository extends JpaRepository<MatchHistory, Long> {
    List<MatchHistory> findByMatchIdOrderByChangedAtAsc(Long matchId);

    // Momento em que o match virou MATCHED pela ultima vez -- fonte da verdade
    // para "30 dias apos o fechamento da contratacao" (Match nao guarda esse
    // timestamp; so createdAt, que para matches nascidos no ranking e bem antes).
    // Retorna null se o match nunca teve transicao registrada para MATCHED.
    @Query("SELECT MAX(h.changedAt) FROM MatchHistory h "
         + "WHERE h.match.id = :matchId AND h.toStatus = 'MATCHED'")
    LocalDateTime findLastMatchedAt(@Param("matchId") Long matchId);
}
