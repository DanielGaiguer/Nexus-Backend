package com.main.nexus.dto;

// Corpo do POST /api/users/me/deletion/confirm -- o token que veio no link do
// e-mail de confirmação (LGPD, exclusão de conta).
public record AccountDeletionConfirmDTO(String token) {}
