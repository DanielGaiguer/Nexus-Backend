package com.main.nexus.dto;

import com.main.nexus.model.LegalDocument;
import java.time.LocalDateTime;

// Item da lista "versoes anteriores" -- sem o conteudo (a lista pode ter varias
// versoes; o corpo so e carregado ao abrir uma versao especifica).
public record LegalDocumentVersionDTO(
        Long id,
        String type,
        String slug,
        Integer version,
        String title,
        String summaryOfChanges,
        boolean active,
        LocalDateTime publishedAt,
        String publishedByAdminEmail
) {
    public static LegalDocumentVersionDTO from(LegalDocument d) {
        return new LegalDocumentVersionDTO(
                d.getId(),
                d.getType().name(),
                d.getType().slug(),
                d.getVersion(),
                d.getTitle(),
                d.getSummaryOfChanges(),
                Boolean.TRUE.equals(d.getActive()),
                d.getPublishedAt(),
                d.getPublishedByAdmin() != null ? d.getPublishedByAdmin().getEmail() : null
        );
    }
}
