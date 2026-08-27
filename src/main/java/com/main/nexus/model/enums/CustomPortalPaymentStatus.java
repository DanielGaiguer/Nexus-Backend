package com.main.nexus.model.enums;

// Situacao de pagamento da assinatura da plataforma personalizada. Sem gateway
// nesta fase — o Admin marca a mao. Nomes em ingles SCREAMING_SNAKE pra bater
// com o resto dos enums do sistema (CompanyStatus, ProjectStatus, ...); os
// rotulos "Em dia / Atrasado / Cancelado" ficam so na UI.
public enum CustomPortalPaymentStatus {
    UP_TO_DATE,  // "Em dia"
    OVERDUE,     // "Atrasado"
    CANCELED     // "Cancelado" — assinatura sem cobranca ativa
}
