package com.main.nexus.repository;

import com.main.nexus.model.CommissionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// Singleton: sempre acessado por CommissionPolicy.SINGLETON_ID (ver CommissionService.getPolicy).
@Repository
public interface CommissionPolicyRepository extends JpaRepository<CommissionPolicy, Long> {
}
