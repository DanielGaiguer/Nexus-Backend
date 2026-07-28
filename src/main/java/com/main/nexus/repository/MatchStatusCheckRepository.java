package com.main.nexus.repository;

import com.main.nexus.model.MatchStatusCheck;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchStatusCheckRepository extends JpaRepository<MatchStatusCheck, Long> {
    boolean existsByMatchId(Long matchId);
    Optional<MatchStatusCheck> findByMatchId(Long matchId);
}
