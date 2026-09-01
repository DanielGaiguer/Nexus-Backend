package com.main.nexus.dto;

// Corpo de PUT /api/admin/companies/{companyId}/observation -- liga/desliga o
// flag "empresa sob observação" (sinalizador manual, sem punição automática).
public record AdminCompanyObservationRequestDTO(boolean underObservation) {}
