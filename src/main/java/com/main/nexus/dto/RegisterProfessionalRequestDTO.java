package com.main.nexus.dto;

import com.main.nexus.model.enums.OpportunityType;
import java.util.List;

public record RegisterProfessionalRequestDTO(
        // Obrigatórios
        String email,
        String password,
        String name,
        String phone,

        // Opcional no cadastro — notificado se ausente
        String cep,

        // Regime de contratação desejado — notificado se ausente
        List<OpportunityType> preferredOpportunityTypes,

        // Pretensões — notificadas se ausentes conforme regime selecionado
        Double expectedSalaryCLT,
        Double expectedSalaryPJ,
        Double freelanceMinExpectation,
        Double freelanceMaxExpectation
) {}