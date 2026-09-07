package com.main.nexus.model.enums;

// Situação do vínculo de um usuário com uma conta empresarial. v1 só tem ACTIVE
// (o backfill e o cadastro da empresa criam membros já ativos). O fluxo de
// convite (etapa futura) acrescenta os estados de convite pendente / remoção --
// é só adicionar o valor aqui.
public enum CompanyMemberStatus {
    ACTIVE
}
