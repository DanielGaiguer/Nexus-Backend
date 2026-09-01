package com.main.nexus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Dispara a cobranca no Mercado Pago APOS a transacao da confirmacao commitar --
// assim a linha de CommissionCharge ja existe no banco e uma falha do MP nunca
// desfaz a confirmacao. @Async para nao segurar a resposta da acao do usuario.
@Component
public class BillingEventListener {

    private static final Logger log = LoggerFactory.getLogger(BillingEventListener.class);

    @Autowired
    private BillingService billingService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommissionChargeCreated(CommissionChargeCreatedEvent event) {
        try {
            billingService.processCharge(event.chargeId());
        } catch (Exception e) {
            log.error("Falha ao processar a cobranca {}: {}", event.chargeId(), e.getMessage(), e);
        }
    }
}
