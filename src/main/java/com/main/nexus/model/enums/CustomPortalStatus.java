package com.main.nexus.model.enums;

// Ciclo de vida da plataforma personalizada em si (CustomPortal), controlado
// manualmente pelo Admin. Nao se confunde com CompanyStatus: suspender/cancelar
// a plataforma NAO mexe no cadastro normal da empresa no Nexus.
public enum CustomPortalStatus {
    ACTIVE,     // plataforma no ar (assinatura em dia ou tolerada pelo Admin)
    SUSPENDED,  // desligada temporariamente (ex.: inadimplencia) — pode voltar a ACTIVE
    CANCELED    // encerrada em definitivo — estado terminal
}
