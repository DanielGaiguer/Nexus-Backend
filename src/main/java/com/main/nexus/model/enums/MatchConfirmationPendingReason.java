package com.main.nexus.model.enums;

// Por que uma MatchConfirmation caiu em PENDING_ADMIN_REVIEW.
public enum MatchConfirmationPendingReason {
    // Os dois confirmaram que houve trabalho, mas os valores informados ficaram
    // fora da tolerancia (max(R$ 50, 2%)).
    VALUE_DIVERGENCE,
    // O prazo de 7 dias estourou com pelo menos um lado sem responder.
    NO_RESPONSE,
    // Um lado diz que o trabalho aconteceu e o outro diz que nao.
    COMPLETION_DISAGREEMENT
}
