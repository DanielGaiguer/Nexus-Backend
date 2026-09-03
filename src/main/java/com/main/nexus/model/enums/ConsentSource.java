package com.main.nexus.model.enums;

// De onde partiu um registro de consentimento -- so para auditoria/trilha.
public enum ConsentSource {
    REGISTRATION,   // checkbox no cadastro
    REACCEPT_GATE   // tela de re-aceite obrigatorio apos nova versao dos Termos
}
