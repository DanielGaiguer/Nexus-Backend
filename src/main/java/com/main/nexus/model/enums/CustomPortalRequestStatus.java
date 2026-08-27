package com.main.nexus.model.enums;

// Ciclo de vida de uma solicitacao de interesse por plataforma personalizada
// (CustomPortalRequest). Mesmo desenho de CompanyStatus: fila -> decisao do Admin.
public enum CustomPortalRequestStatus {
    PENDING,   // contratante pediu, aguardando analise do Admin
    APPROVED,  // Admin aprovou -> um CustomPortal foi criado e vinculado a esta solicitacao
    REJECTED   // Admin recusou (com motivo em decisionReason); contratante pode pedir de novo
}
