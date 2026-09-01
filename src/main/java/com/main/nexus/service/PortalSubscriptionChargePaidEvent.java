package com.main.nexus.service;

// Publicado por PortalSubscriptionService quando uma mensalidade da plataforma
// vira PAID. Um @TransactionalEventListener(AFTER_COMMIT) dispara a emissao da
// NFS-e FORA da transacao da cobranca -- ver NfseEventListener.
public record PortalSubscriptionChargePaidEvent(Long chargeId) {}
