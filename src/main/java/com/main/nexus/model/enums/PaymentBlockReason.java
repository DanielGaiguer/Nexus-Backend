package com.main.nexus.model.enums;

// Por que um contratante esta impedido de fechar novas contratacoes (camada
// financeira, Prompt 5). Ver CompanyBillingProfile / BillingService.
public enum PaymentBlockReason {
    // Ha uma comissao a cobrar e o contratante nao tem cartao cadastrado.
    NO_CARD_ON_FILE,
    // A ultima cobranca de comissao foi recusada pelo Mercado Pago.
    CHARGE_DECLINED
}
