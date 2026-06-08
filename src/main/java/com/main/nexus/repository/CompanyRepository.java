package com.main.nexus.repository;

import com.main.nexus.model.Company;
import com.main.nexus.model.enums.CompanyStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByUserId(Long userId);
    boolean existsByTaxId(String taxId);
    List<Company> findByStatus(CompanyStatus status);
}