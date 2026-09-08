package com.main.nexus.dto;

// Edição de uma nota interna -- só o texto. O vínculo com o Match é definido na criação e não
// muda no PUT.
public record CompanyCandidateNoteUpdateDTO(
        String body
) {}
