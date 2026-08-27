package com.main.nexus.repository;

import com.main.nexus.model.CustomPortalStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomPortalStatusHistoryRepository
        extends JpaRepository<CustomPortalStatusHistory, Long> {

    List<CustomPortalStatusHistory> findByCustomPortalIdOrderByChangedAtDesc(Long customPortalId);
}
