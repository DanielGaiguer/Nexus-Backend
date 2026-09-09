package com.main.nexus.dto;

// Link de reproducao assinado, de curta validade. `expiresInSeconds` existe pro front saber
// quando pedir outro em vez de deixar o player quebrar no meio.
public record ScreeningVideoPlaybackDTO(
        Long questionId,
        String url,
        Integer expiresInSeconds,
        Integer durationSeconds
) {}
