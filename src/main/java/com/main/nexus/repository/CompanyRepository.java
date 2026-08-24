package com.main.nexus.repository;

import com.main.nexus.model.Company;
import com.main.nexus.model.enums.CompanyStatus;
import com.main.nexus.model.enums.CompanyType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByUserId(Long userId);
    boolean existsByTaxId(String taxId);
    List<Company> findByStatus(CompanyStatus status);
    List<Company> findByType(CompanyType type);

    List<Company> findTop5ByStatusNotOrderByUserCreatedAtDesc(CompanyStatus status);
    Page<Company> findByStatusAndCompanyNameContainingIgnoreCase(
            CompanyStatus status, String companyName, Pageable pageable);
    Page<Company> findByStatusAndCompanyNameContainingIgnoreCaseAndType(
            CompanyStatus status, String companyName, CompanyType type, Pageable pageable);
}