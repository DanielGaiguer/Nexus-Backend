package com.main.nexus.dto;

// Skill mencionada pela empresa no texto bruto. matchedSkillId só vem preenchido quando
// o nome extraído casa (normalizado, case-insensitive) com uma skill ativa do catálogo —
// a IA nunca cria skill nova, só sugere um vínculo com o que já existe (RN49).
public record AiSkillSuggestionDTO(
        String extractedName,
        Long matchedSkillId,
        String matchedSkillName,
        boolean foundInCatalog
) {}
