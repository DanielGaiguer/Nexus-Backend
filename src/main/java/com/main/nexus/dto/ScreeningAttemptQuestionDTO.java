package com.main.nexus.dto;

import com.main.nexus.model.enums.ScreeningQuestionType;
import java.util.List;

// Versão da questão exibida ao profissional ENQUANTO ele responde -- sem correctOptionIndex,
// que só é revelado depois da submissão (ver ScreeningAnswerDetailDTO).
public record ScreeningAttemptQuestionDTO(
        Long id,
        ScreeningQuestionType type,
        String prompt,
        List<String> options,
        // VIDEO_RESPONSE: se o candidato já subiu um vídeo pra esta questão nesta tentativa --
        // é o que faz a tela oferecer "regravar" em vez de "gravar", e o que deixa ele fechar a
        // aba no meio sem perder o que já subiu.
        boolean videoUploaded
) {}
