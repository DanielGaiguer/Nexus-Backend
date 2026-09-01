package com.main.nexus.dto;

// Corpo de POST /api/admin/invoices/{id}/simulate. outcome = "authorized" | "denied".
public record SimulateNfseRequestDTO(String outcome) {}
