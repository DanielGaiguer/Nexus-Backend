package com.main.nexus.dto;

import java.time.LocalDateTime;

// Estado do consentimento de gravacao DESTA tentativa. `text` e sempre o texto vigente quando
// ainda nao houve aceite, e o texto EXATO que a pessoa leu quando ja houve -- nunca o vigente
// sobreposto ao antigo.
public record ScreeningVideoConsentDTO(
        Long invitationId,
        boolean accepted,
        LocalDateTime acceptedAt,
        String text
) {}
