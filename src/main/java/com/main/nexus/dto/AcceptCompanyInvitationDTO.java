package com.main.nexus.dto;

// Corpo de POST /api/company/invitations/accept (rota pública). O convidado
// escolhe a senha aqui -- é a criação da conta dele -- e aceita os Termos na
// mesma request (mesma trilha de consentimento do cadastro normal, ver
// RegisterCompanyRequestDTO).
public record AcceptCompanyInvitationDTO(
        String token,
        String password,
        Boolean acceptedTermsOfUse,
        Boolean acceptedMarketingCommunications,
        Boolean acceptedAlgorithmImprovement
) {}
