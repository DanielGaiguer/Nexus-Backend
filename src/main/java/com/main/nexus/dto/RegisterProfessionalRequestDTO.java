package com.main.nexus.dto;

import com.main.nexus.model.enums.OpportunityType;
import java.util.List;

public record RegisterProfessionalRequestDTO(
        // Obrigatórios
        String email,
        String password,
        String name,
        String phone,
        String cep,
        List<OpportunityType> preferredOpportunityTypes,
        Double expectedSalaryCLT,
        Double expectedSalaryPJ,
        Double freelanceMinExpectation,
        Double freelanceMaxExpectation
) {}