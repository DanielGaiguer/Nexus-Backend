package com.main.nexus.dto;

import com.main.nexus.model.LegalDocument;
import java.time.LocalDateTime;

// Uma versao de documento legal, com conteudo. Usado pelas rotas publicas
// (/api/public/legal/**) e pelo painel do Admin.
public record LegalDocumentDTO(
        Long id,
        String type,
        String slug,
        Integer version,
        String title,
        String content,
        String summaryOfChanges,
        boolean active,
        LocalDateTime publishedAt,
        String publishedByAdminEmail
) {
    public static LegalDocumentDTO from(LegalDocument d) {
        return new LegalDocumentDTO(
                d.getId(),
                d.getType().name(),
                d.getType().slug(),
                d.getVersion(),
                d.getTitle(),
                d.getContent(),
                d.getSummaryOfChanges(),
                Boolean.TRUE.equals(d.getActive()),
                d.getPublishedAt(),
                d.getPublishedByAdmin() != null ? d.getPublishedByAdmin().getEmail() : null
        );
    }
}
