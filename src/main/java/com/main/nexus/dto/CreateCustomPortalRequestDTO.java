package com.main.nexus.dto;

// Corpo do pedido do contratante em POST /api/company/custom-portal/requests.
// message e opcional (observacao livre).
public record CreateCustomPortalRequestDTO(String message) {}
