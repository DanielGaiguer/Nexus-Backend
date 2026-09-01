package com.main.nexus.repository;

import com.main.nexus.model.MatchStatusCheck;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.MatchOutcome;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchStatusCheckRepository extends JpaRepository<MatchStatusCheck, Long> {

    // Agora ha ate 2 linhas por match (uma por lado) -- por isso List, nao Optional.
    List<MatchStatusCheck> findByMatchId(Long matchId);

    Optional<MatchStatusCheck> findByMatchIdAndAnsweredBy(Long matchId, AuthorType answeredBy);

    boolean existsByMatchId(Long matchId);

    boolean existsByMatchIdAndAnsweredBy(Long matchId, AuthorType answeredBy);

    // "Algum lado respondeu com este outcome?" -- usado pelo bloqueio de
    // avaliacao quando houve NO_CONTACT_YET (ReviewService.save).
    boolean existsByMatchIdAndOutcome(Long matchId, MatchOutcome outcome);
}
