package com.main.nexus.dto;

import com.main.nexus.model.enums.ContractType;
import com.main.nexus.model.enums.ExperienceLevel;
import com.main.nexus.model.enums.Modality;
import com.main.nexus.model.enums.OpportunityType;
import com.main.nexus.model.enums.ProjectType;
import java.time.LocalDate;
import java.util.List;

// DTO intermediário de extração por IA — nunca é persistido e nunca é o mesmo objeto que
// ProjectRequestDTO. Só serve para pré-preencher o formulário de criação; a empresa revisa e
// edita livremente antes de qualquer POST /api/projects real. Todo campo é opcional aqui,
// mesmo os que ProjectService.validateByType() exige na criação — "não mencionado no texto"
// é sempre null, nunca um valor inventado.
//
// Os nomes espelham ProjectRequestDTO onde faz sentido (workMode, type, etc.) para facilitar
// o mapeamento direto no formulário do frontend.
public record AiOpportunityExtractionDTO(
        String title,
        String description,
        OpportunityType opportunityType,   // null quando ambíguo — empresa decide manualmente
        ProjectType type,
        Modality workMode,
        ExperienceLevel experienceLevel,
        Integer maxPositions,

        // Exclusivos de PROJECT
        Double minimumBudget,
        Double maximumBudget,
        LocalDate deadline,

        // Exclusivos de JOB
        ContractType contractType,
        Double monthlySalaryMin,
        Double monthlySalaryMax,
        List<String> benefits,
        LocalDate startDate,
        Integer workloadHoursPerWeek,

        // Localização — só o CEP se vier explícito no texto. city/uf/lat/long nunca aparecem
        // aqui: continuam sendo derivados exclusivamente do CEP via GeolocationService, só no
        // momento em que a empresa de fato submete a criação.
        String cep,

        List<AiSkillSuggestionDTO> requiredSkills,
        List<AiSkillSuggestionDTO> niceToHaveSkills
) {}
