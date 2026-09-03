package com.main.nexus.model.enums;

// Tipos de documento legal versionado (LGPD). Uma versao ativa por tipo em
// tb_legal_document -- ver LegalDocumentService.
public enum LegalDocumentType {
    TERMS_OF_USE,
    PRIVACY_POLICY;

    // Slug usado nas rotas publicas (/api/public/legal/{slug}) e no frontend
    // (/terms, /privacy). Mantido curto e em ingles, seguindo a convencao da
    // arvore de rotas ja existente (/pro, /company, /admin...).
    public String slug() {
        return this == TERMS_OF_USE ? "terms" : "privacy";
    }

    public static LegalDocumentType fromSlug(String slug) {
        if (slug == null) {
            throw new IllegalArgumentException("Legal document slug is required.");
        }
        return switch (slug.toLowerCase()) {
            case "terms", "terms-of-use", "termos" -> TERMS_OF_USE;
            case "privacy", "privacy-policy", "privacidade" -> PRIVACY_POLICY;
            default -> throw new IllegalArgumentException("Unknown legal document slug: " + slug);
        };
    }
}
