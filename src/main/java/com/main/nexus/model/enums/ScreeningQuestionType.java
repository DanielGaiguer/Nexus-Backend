package com.main.nexus.model.enums;

public enum ScreeningQuestionType {
    MULTIPLE_CHOICE,
    ESSAY,
    // Item de escala Likert de 5 pontos, exclusivo de etapas BEHAVIORAL. Reaproveita
    // ScreeningAnswer.selectedOptionIndex (0..4 = "Discordo totalmente".."Concordo totalmente"),
    // então não precisou de coluna nova pra resposta. `options` fica VAZIA de propósito: a escala
    // é fixa e igual pra todo item, guardar os mesmos 5 rótulos em centenas de linhas seria só
    // duplicação -- quem conhece os rótulos é o front (e o export, via
    // ScreeningAnswer.likertLabel). Nunca tem gabarito: não existe resposta certa.
    LIKERT_SCALE,
    // Resposta em video assincrona, exclusiva de etapa VIDEO. O arquivo NAO trafega pelo submit:
    // sobe direto do browser pro Supabase por signed URL e e confirmado num endpoint proprio
    // (ver ScreeningVideoService); o submit so amarra. Sem gabarito e sem options -- a empresa
    // assiste e decide, igual a uma dissertativa.
    VIDEO_RESPONSE
}
