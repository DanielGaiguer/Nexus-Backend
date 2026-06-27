package com.main.nexus.repository;

import com.main.nexus.model.ReputationMetrics;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReputationMetricsRepository extends JpaRepository<ReputationMetrics, Long> {
    Optional<ReputationMetrics> findByProfessionalId(Long professionalId);
    Optional<ReputationMetrics> findByCompanyId(Long companyId);
}