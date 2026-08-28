package com.main.nexus.model.enums;

// Tipos de evento de visita registrados na página pública da plataforma
// personalizada (analytics do contratante).
public enum CustomPortalEventType {
    PAGE_VIEW,     // abriu a home ou uma vaga
    APPLY_CLICK,   // clicou em "Candidatar-se" / "Demonstrar interesse"
    SESSION_END    // saiu da página — carrega a duração da sessão em segundos
}
