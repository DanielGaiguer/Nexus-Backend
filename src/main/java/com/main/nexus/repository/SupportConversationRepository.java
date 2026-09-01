package com.main.nexus.repository;

import com.main.nexus.model.SupportConversation;
import com.main.nexus.model.enums.SupportConversationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportConversationRepository extends JpaRepository<SupportConversation, Long> {

    List<SupportConversation> findByStatusOrderByCreatedAtDesc(SupportConversationStatus status);

    List<SupportConversation> findAllByOrderByCreatedAtDesc();

    Optional<SupportConversation> findFirstByUserIdAndStatus(
            Long userId, SupportConversationStatus status);

    List<SupportConversation> findByUserIdOrderByCreatedAtDesc(Long userId);
}
