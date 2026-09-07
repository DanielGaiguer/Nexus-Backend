package com.main.nexus.model.enums;

// Ciclo de vida de um convite para entrar numa conta empresarial.
// PENDING -> ACCEPTED (o convidado criou a conta) | REVOKED (o OWNER cancelou)
// | EXPIRED (passou o prazo sem aceite). Estados terminais nunca voltam a PENDING.
public enum CompanyInvitationStatus {
    PENDING,
    ACCEPTED,
    REVOKED,
    EXPIRED
}
