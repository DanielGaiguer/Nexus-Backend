package com.main.nexus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Dispara a emissão da NFS-e APÓS a transação da cobrança commitar -- a linha de
// CommissionCharge já está PAID no banco e uma falha do eNotas nunca desfaz a
// cobrança. @Async para não segurar a resposta da ação/webhook que confirmou o
// pagamento. (Prompt 6)
@Component
public class NfseEventListener {

    private static final Logger log = LoggerFactory.getLogger(NfseEventListener.class);

    @Autowired
    private NfseService nfseService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommissionChargePaid(CommissionChargePaidEvent event) {
        try {
            nfseService.issueFor(event.chargeId());
        } catch (Exception e) {
            log.error("Falha ao emitir a NFS-e da cobranca {}: {}", event.chargeId(), e.getMessage(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPortalSubscriptionChargePaid(PortalSubscriptionChargePaidEvent event) {
        try {
            nfseService.issueForPortalCharge(event.chargeId());
        } catch (Exception e) {
            log.error("Falha ao emitir a NFS-e da mensalidade {}: {}",
                    event.chargeId(), e.getMessage(), e);
        }
    }
}
