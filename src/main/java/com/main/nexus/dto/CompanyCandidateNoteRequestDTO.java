package com.main.nexus.dto;

// Criação de uma nota interna. `matchId` opcional -- se informado, o match precisa pertencer a
// um projeto da empresa logada E ao profissional da rota.
public record CompanyCandidateNoteRequestDTO(
        Long matchId,
        String body
) {}
