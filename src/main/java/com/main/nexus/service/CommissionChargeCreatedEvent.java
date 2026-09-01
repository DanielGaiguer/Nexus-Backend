package com.main.nexus.service;

// Publicado por CommissionService.onHireConfirmed quando uma cobranca de comissao
// e criada. Um @TransactionalEventListener(AFTER_COMMIT) dispara a cobranca no
// Mercado Pago FORA da transacao da confirmacao -- ver BillingEventListener.
public record CommissionChargeCreatedEvent(Long chargeId) {}
