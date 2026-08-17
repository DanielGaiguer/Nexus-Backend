package com.main.nexus.repository;

import com.main.nexus.model.ProfessionalCredential;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfessionalCredentialRepository extends JpaRepository<ProfessionalCredential, Long> {
    List<ProfessionalCredential> findByProfessionalId(Long professionalId);
}
