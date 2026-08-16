package com.main.nexus.repository;

import com.main.nexus.model.Professional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfessionalRepository extends JpaRepository<Professional, Long> {
    Optional<Professional> findByUserId(Long userId);
    Page<Professional> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Optional<Professional> findByGithubId(String githubId);

    // Disponíveis sem acessar a plataforma há mais de :threshold — usa a data de login
    // mais recente, ou a de cadastro se ele nunca chegou a logar de novo depois de
    // criar a conta. Usado pelo job de inatividade (ProfessionalInactivityService).
    @Query("SELECT p FROM Professional p WHERE p.available = true " +
           "AND COALESCE(p.user.lastLoginAt, p.user.createdAt) < :threshold")
    List<Professional> findInactiveAvailableProfessionals(@Param("threshold") LocalDateTime threshold);
}