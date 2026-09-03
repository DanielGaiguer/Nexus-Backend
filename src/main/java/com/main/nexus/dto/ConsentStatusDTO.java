package com.main.nexus.dto;

// Estado de consentimento do usuario logado -- consumido pelo layout autenticado
// do frontend (tela de re-aceite) e pelo ConsentGateFilter indiretamente.
public record ConsentStatusDTO(
        // true => usuario precisa re-aceitar os Termos antes de seguir usando a
        // plataforma. Vale tanto para "saiu versao nova" quanto para "conta
        // criada via OAuth sem nunca ter passado por um checkbox".
        boolean mustReacceptTerms,
        Integer activeTermsVersion,
        Integer acceptedTermsVersion,      // versao que o usuario aceitou por ultimo (pode ser nula)
        String termsSummaryOfChanges,      // "o que mudou" da versao ativa (nulo na v1)
        Integer activePrivacyVersion,
        // Estado atual das duas finalidades opcionais -- para a tela de re-aceite
        // pre-marcar os toggles com o ultimo valor conhecido.
        boolean marketingConsent,
        boolean algorithmImprovementConsent
) {}
