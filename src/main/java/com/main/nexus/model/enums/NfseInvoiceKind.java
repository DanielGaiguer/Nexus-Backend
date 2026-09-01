package com.main.nexus.model.enums;

// Origem de uma NFS-e. Derivado de qual cobranca esta preenchida no NfseInvoice
// (nao ha coluna discriminadora).
public enum NfseInvoiceKind {
    COMMISSION,           // comissao por contratacao fechada (Prompt 6)
    PORTAL_SUBSCRIPTION   // mensalidade da plataforma personalizada
}
