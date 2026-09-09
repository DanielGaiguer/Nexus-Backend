package com.main.nexus.dto;

// Confirmacao pos-upload: o cliente diz qual objeto subiu e quanto tempo o video tem. O
// `objectUrl` recebido no ticket volta aqui como `videoUrl`.
//
// `durationSeconds` e informativo e NAO e verificado -- medido pelo MediaRecorder no client. O
// backend so garante o teto de TAMANHO, que ele mede no proprio Supabase.
public record ScreeningVideoConfirmRequestDTO(
        Long questionId,
        String videoUrl,
        Integer durationSeconds
) {}
