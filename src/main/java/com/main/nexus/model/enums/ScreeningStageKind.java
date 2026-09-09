package com.main.nexus.model.enums;

// Natureza de uma ScreeningStage -- decide como a etapa é montada, respondida, corrigida e
// decidida. Não é só um rótulo de UI: cada valor carrega regras diferentes.
//
//  QUESTIONS   -- o comportamento original (e o default de toda etapa já existente): a empresa
//                 escreve as próprias perguntas (MULTIPLE_CHOICE/ESSAY), o candidato responde, a
//                 empresa aprova ou reprova a etapa manualmente.
//  BEHAVIORAL  -- inventário Big Five (Likert 1-5) com banco de itens FIXO, de plataforma (ver
//                 BehavioralItem/BehavioralItemSeed). A empresa nunca escreve item comportamental
//                 -- só liga ou desliga a etapa. É SEMPRE informativa: nunca reprova, nunca
//                 bloqueia avanço (ver ScreeningInvitationService.submit) -- decisão tomada de
//                 propósito para não expor a plataforma a uma reprovação por traço de
//                 personalidade.
//  VIDEO       -- resposta em vídeo assíncrona. Reservado aqui para não precisar de um segundo
//                 ALTER TABLE depois; ainda sem comportamento próprio (Prompt 2/7).
public enum ScreeningStageKind {
    QUESTIONS,
    BEHAVIORAL,
    VIDEO
}
