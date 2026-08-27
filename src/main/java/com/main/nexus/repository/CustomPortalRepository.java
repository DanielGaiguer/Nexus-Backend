package com.main.nexus.repository;

import com.main.nexus.model.CustomPortal;
import com.main.nexus.model.enums.CustomPortalStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomPortalRepository extends JpaRepository<CustomPortal, Long> {

    Optional<CustomPortal> findByCompanyId(Long companyId);

    boolean existsByCompanyId(Long companyId);

    boolean existsBySubdomainIgnoreCase(String subdomain);

    Optional<CustomPortal> findBySubdomainIgnoreCase(String subdomain);

    List<CustomPortal> findAllByOrderByCreatedAtDesc();

    // Usado pelo job diario de aviso de vencimento.
    List<CustomPortal> findByStatusAndNextDueDateLessThanEqual(
            CustomPortalStatus status, LocalDate limit);
}
