package com.main.nexus.repository;

import com.main.nexus.model.CustomPortalRequest;
import com.main.nexus.model.enums.CustomPortalRequestStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomPortalRequestRepository extends JpaRepository<CustomPortalRequest, Long> {

    List<CustomPortalRequest> findByStatusOrderByRequestedAtAsc(CustomPortalRequestStatus status);

    List<CustomPortalRequest> findAllByOrderByRequestedAtDesc();

    boolean existsByCompanyIdAndStatus(Long companyId, CustomPortalRequestStatus status);

    Optional<CustomPortalRequest> findFirstByCompanyIdOrderByRequestedAtDesc(Long companyId);
}
