package com.main.nexus.model.enums;

// Seções da sidebar que usam badge do "Padrão B" (houve uma atualização que o
// usuário ainda não viu) e por isso precisam de um marco de "visto" por usuário.
// Só entram aqui as seções sem um status de pendência natural -- as demais
// (Padrão A) contam registros por status e não guardam "visto". Ver
// SectionView / SidebarBadgeService.
public enum SidebarSection {
    // Profissional -- "um match que você enviou e o outro lado já respondeu".
    PRO_MATCHES,
    // Profissional -- "uma proposta que você enviou mudou de status".
    PRO_PROPOSALS,
    // Contratante -- "status da solicitação de portal mudou / aviso de assinatura".
    COMPANY_CUSTOM_PORTAL
}
