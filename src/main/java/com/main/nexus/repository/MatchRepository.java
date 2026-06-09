package com.main.nexus.repository;

import com.main.nexus.model.Match;
import com.main.nexus.model.enums.StatusMatch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findByProjectId(Long projectId);
    List<Match> findByProfessionalId(Long professionalId);
    List<Match> findByStatus(StatusMatch status);
    Optional<Match> findByProjectIdAndProfessionalId(Long projectId, Long professionalId);
    List<Match> findByProjectCompanyId(Long companyId);

    long countByStatus(StatusMatch status);
}