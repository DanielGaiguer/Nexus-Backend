package com.main.nexus.repository;

import com.main.nexus.model.CompanyMember;
import com.main.nexus.model.enums.CompanyMemberRole;
import com.main.nexus.model.enums.CompanyMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyMemberRepository extends JpaRepository<CompanyMember, Long> {

    // Um resultado no máximo enquanto UNIQUE(user_id) valer (decisão v1).
    Optional<CompanyMember> findByUserId(Long userId);

    Optional<CompanyMember> findByUserIdAndStatus(Long userId, CompanyMemberStatus status);

    List<CompanyMember> findByCompanyId(Long companyId);

    List<CompanyMember> findByCompanyIdAndStatus(Long companyId, CompanyMemberStatus status);

    long countByCompanyIdAndStatus(Long companyId, CompanyMemberStatus status);

    List<CompanyMember> findByCompanyIdAndStatusAndRole(
            Long companyId, CompanyMemberStatus status, CompanyMemberRole role);

    long countByCompanyIdAndStatusAndRole(
            Long companyId, CompanyMemberStatus status, CompanyMemberRole role);

    Optional<CompanyMember> findByCompanyIdAndUserId(Long companyId, Long userId);

    boolean existsByCompanyIdAndUserIdAndStatus(
            Long companyId, Long userId, CompanyMemberStatus status);

    long countByCompanyIdAndRole(Long companyId, CompanyMemberRole role);
}
