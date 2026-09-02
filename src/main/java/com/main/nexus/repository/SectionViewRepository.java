package com.main.nexus.repository;

import com.main.nexus.model.SectionView;
import com.main.nexus.model.enums.SidebarSection;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SectionViewRepository extends JpaRepository<SectionView, Long> {

    Optional<SectionView> findByUserIdAndSection(Long userId, SidebarSection section);

    // Upsert atômico do "visto" da seção -- ver SidebarBadgeService.markSeen para
    // o porquê (chamadas concorrentes no 1º acesso). MySQL, como o resto do
    // projeto (ddl-auto=update sobre MySQL; ver SchemaFixups). `section` entra
    // como o nome do enum (mesmo texto que @Enumerated(STRING) grava).
    @Modifying
    @Query(value = "INSERT INTO tb_section_view (user_id, section, seen_at) "
                 + "VALUES (:userId, :section, :seenAt) "
                 + "ON DUPLICATE KEY UPDATE seen_at = :seenAt",
           nativeQuery = true)
    void upsertSeenAt(@Param("userId") Long userId,
                      @Param("section") String section,
                      @Param("seenAt") LocalDateTime seenAt);
}
