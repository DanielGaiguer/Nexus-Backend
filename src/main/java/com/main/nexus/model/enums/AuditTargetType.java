package com.main.nexus.model.enums;

// Tipo do id que um endpoint administrativo recebe como alvo, para o
// DataAccessAuditAspect resolver de volta ao User titular do dado acessado.
// NONE = acesso amplo sem um alvo único (ex.: listar todos os usuários).
public enum AuditTargetType {
    USER,
    PROFESSIONAL,
    COMPANY,
    SUPPORT_CONVERSATION,
    CUSTOM_PORTAL,
    CUSTOM_PORTAL_REQUEST,
    NONE
}
