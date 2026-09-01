package com.main.nexus.repository;

import com.main.nexus.model.CompanyBillingProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyBillingProfileRepository extends JpaRepository<CompanyBillingProfile, Long> {
    Optional<CompanyBillingProfile> findByCompanyId(Long companyId);

    // Quantos contratantes estao bloqueados por pendencia de pagamento -- KPI do
    // painel financeiro do Admin (Prompt 7).
    long countByPaymentBlockedTrue();
}
