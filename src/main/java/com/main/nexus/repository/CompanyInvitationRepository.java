package com.main.nexus.repository;

import com.main.nexus.model.CompanyInvitation;
import com.main.nexus.model.enums.CompanyInvitationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyInvitationRepository extends JpaRepository<CompanyInvitation, Long> {

    List<CompanyInvitation> findByCompanyIdAndStatus(Long companyId, CompanyInvitationStatus status);

    boolean existsByCompanyIdAndEmailAndStatus(
            Long companyId, String email, CompanyInvitationStatus status);
}
