package com.main.nexus.repository;

import com.main.nexus.model.MatchHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchHistoryRepository extends JpaRepository<MatchHistory, Long> {
    List<MatchHistory> findByMatchIdOrderByChangedAtAsc(Long matchId);
}
