package com.main.nexus.dto;

// Corpo de POST /api/admin/commission-charges/{id}/simulate. outcome = "approved" | "rejected".
public record SimulateChargeRequestDTO(String outcome) {}
