package com.main.nexus.repository;

import com.main.nexus.model.CandidateEvaluation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CandidateEvaluationRepository extends JpaRepository<CandidateEvaluation, Long> {

    // Todos os pareceres de um match, mais antigo primeiro -- base do consolidado (média + lista).
    List<CandidateEvaluation> findByMatchIdOrderByCreatedAtAsc(Long matchId);

    // O parecer de um avaliador específico num match -- chave do upsert /evaluations/mine.
    Optional<CandidateEvaluation> findByMatchIdAndEvaluatorId(Long matchId, Long evaluatorId);

    // [matchId, AVG(rating), COUNT] para vários matches de uma vez -- alimenta average/count de
    // cada card do board numa única query (evita N+1 no getBoard).
    @Query("SELECT e.match.id, AVG(e.rating), COUNT(e) FROM CandidateEvaluation e "
         + "WHERE e.match.id IN :matchIds GROUP BY e.match.id")
    List<Object[]> aggregateByMatchIds(@Param("matchIds") Collection<Long> matchIds);
}
