package com.main.nexus.repository;

import com.main.nexus.model.CompanyFiscalProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyFiscalProfileRepository extends JpaRepository<CompanyFiscalProfile, Long> {
    Optional<CompanyFiscalProfile> findByCompanyId(Long companyId);
}
