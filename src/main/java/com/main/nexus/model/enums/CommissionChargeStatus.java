package com.main.nexus.model.enums;

// Estado de uma cobranca de comissao no Mercado Pago (camada financeira, Prompt 5).
public enum CommissionChargeStatus {
    // Criada, aguardando a 1a tentativa de cobranca (ou sem cartao no arquivo).
    PENDING,
    // Enviada ao Mercado Pago, aguardando o resultado (resposta sincrona
    // "in_process"/"pending" ou o webhook).
    PROCESSING,
    // Paga -- confirmada pelo Mercado Pago.
    PAID,
    // Recusada pelo Mercado Pago (cartao sem limite, invalido, etc.). Bloqueia
    // o contratante ate ele regularizar.
    FAILED,
    // Cancelada manualmente (ex.: contratacao anulada). Nao cobra.
    CANCELED
}
