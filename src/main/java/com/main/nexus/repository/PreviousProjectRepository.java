package com.main.nexus.repository;

import com.main.nexus.model.PreviousProject;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PreviousProjectRepository extends JpaRepository<PreviousProject, Long> {
    List<PreviousProject> findByProfessionalId(Long professionalId);

    // LGPD -- exclusão de conta: portfólio é conteúdo autoral do próprio
    // profissional, sem função de integridade para terceiros -> apagado.
    void deleteByProfessionalId(Long professionalId);
}