package com.main.nexus.dto;

// Corpo de POST /api/support/conversations -- o proprio usuario (profissional ou
// contratante) abre um chamado de suporte. `subject` e opcional; `message` e
// obrigatoria (descreve o motivo do contato).
public record OpenSupportTicketRequestDTO(
        String subject,
        String message
) {}
