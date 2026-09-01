package com.main.nexus.dto;

// Corpo de POST /api/admin/confirmations/{matchId}/mark-unconfirmable -- o Admin
// marca que nao foi possivel confirmar (sem valor, sem comissao). `note` opcional.
public record AdminUnconfirmableRequestDTO(String note) {}
