package com.main.nexus.model.enums;

// Ciclo de vida de uma conversa de suporte (Admin <-> usuario).
public enum SupportConversationStatus {
    // Aberta pelo Admin -- os dois lados podem trocar mensagens.
    OPEN,
    // Fechada pelo Admin -- historico so leitura, ninguem envia mais.
    CLOSED
}
