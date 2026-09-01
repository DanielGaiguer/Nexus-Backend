package com.main.nexus.model.enums;

// Como a MatchConfirmation chegou ao seu estado terminal. Nulo enquanto
// AWAITING_RESPONSES ou PENDING_ADMIN_REVIEW (ainda nao resolvida).
public enum MatchConfirmationResolution {
    // Reconciliacao automatica do Prompt 2: os dois lados responderam e bateram
    // (CONFIRMED com valores dentro da tolerancia, ou CLOSED_NO_CHARGE com os
    // dois dizendo que nao houve trabalho).
    PARTIES_AGREED,
    // O Admin definiu o valor final a mao apos contato com as partes -> CONFIRMED.
    ADMIN_SET_VALUE,
    // O Admin nao conseguiu confirmar -> CLOSED_UNRESOLVED, sem valor e sem comissao.
    ADMIN_COULD_NOT_CONFIRM
}
