package com.main.nexus.dto;

// Corpo do POST da tela de re-aceite. O aceite dos Termos e obrigatorio; as
// duas finalidades opcionais viajam junto para o usuario poder ajusta-las na
// mesma tela (a Politica de Privacidade tambem pode ter mudado). Nulo numa
// opcional = "nao mexeu", mantem o ultimo valor.
public record ReacceptConsentDTO(
        Boolean acceptedTermsOfUse,
        Boolean acceptedMarketingCommunications,
        Boolean acceptedAlgorithmImprovement
) {}
