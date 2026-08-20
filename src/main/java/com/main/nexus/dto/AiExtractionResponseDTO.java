package com.main.nexus.dto;

import java.util.List;

// Resposta ao frontend do endpoint de autopreenchimento. lowConfidenceFields carrega nomes
// de campos de AiOpportunityExtractionDTO (ex: "workMode", "monthlySalaryMax") em que a IA
// fez uma inferência mais fraca — o frontend usa isso para destacar visualmente esses campos
// além da marcação padrão de "sugestão da IA, revise antes de publicar".
public record AiExtractionResponseDTO(
        AiOpportunityExtractionDTO suggestion,
        List<String> lowConfidenceFields
) {}
