package com.main.nexus.service;

// Publicado por BillingService.reconcile quando uma cobrança é confirmada como
// PAID. Um @TransactionalEventListener(AFTER_COMMIT) dispara a emissão da NFS-e
// FORA da transação da cobrança -- ver NfseEventListener.
public record CommissionChargePaidEvent(Long chargeId) {}
