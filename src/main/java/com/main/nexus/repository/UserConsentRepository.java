package com.main.nexus.repository;

import com.main.nexus.model.UserConsent;
import com.main.nexus.model.enums.ConsentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    // Estado atual de uma finalidade = linha mais recente por (user, type).
    Optional<UserConsent> findTopByUserIdAndTypeOrderByCreatedAtDesc(Long userId, ConsentType type);

    // Usado pelo ConsentGateFilter e pelo endpoint de status: o usuario tem um
    // aceite valido dos Termos na versao ativa?
    boolean existsByUserIdAndTypeAndGrantedTrueAndDocumentVersion(
            Long userId, ConsentType type, Integer documentVersion);

    // Trilha completa de um usuario (tela de re-aceite / export do Prompt 3).
    List<UserConsent> findByUserIdOrderByCreatedAtAsc(Long userId);
}
