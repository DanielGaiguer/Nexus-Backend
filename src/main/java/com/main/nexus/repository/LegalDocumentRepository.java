package com.main.nexus.repository;

import com.main.nexus.model.LegalDocument;
import com.main.nexus.model.enums.LegalDocumentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, Long> {

    Optional<LegalDocument> findByTypeAndActiveTrue(LegalDocumentType type);

    List<LegalDocument> findByTypeOrderByVersionDesc(LegalDocumentType type);

    Optional<LegalDocument> findByTypeAndVersion(LegalDocumentType type, Integer version);

    boolean existsByType(LegalDocumentType type);

    @Query("select max(d.version) from LegalDocument d where d.type = ?1")
    Integer findMaxVersion(LegalDocumentType type);
}
