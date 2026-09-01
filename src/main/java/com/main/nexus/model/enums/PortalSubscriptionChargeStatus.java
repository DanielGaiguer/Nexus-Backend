package com.main.nexus.model.enums;

// Situacao de uma cobranca mensal da assinatura da plataforma personalizada
// (PortalSubscriptionCharge). Mesmo desenho de CommissionChargeStatus.
public enum PortalSubscriptionChargeStatus {
    PENDING,     // ciclo em aberto, ainda nao cobrado
    PROCESSING,  // enviado ao Mercado Pago / aguardando decisao no modo simulado
    PAID,        // pago
    FAILED,      // recusado pelo cartao
    CANCELED     // ciclo cancelado (plataforma descontinuada / assinatura encerrada)
}
