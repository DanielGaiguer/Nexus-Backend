package com.main.nexus.dto;

import java.util.List;

// Visao do painel do Admin: a versao ativa + o historico completo de cada tipo.
public record AdminLegalOverviewDTO(
        TypeView termsOfUse,
        TypeView privacyPolicy
) {
    public record TypeView(
            String type,
            String slug,
            LegalDocumentDTO active,
            List<LegalDocumentVersionDTO> history
    ) {}
}
