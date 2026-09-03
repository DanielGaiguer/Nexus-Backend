package com.main.nexus.model.enums;

// Finalidades de tratamento de dados com base legal distinta (LGPD).
//
// TERMS_OF_USE            -- obrigatorio: sem aceite, sem cadastro. E a moldura
//                           contratual do uso da conta; re-aceite obrigatorio a
//                           cada nova versao publicada (ver ConsentGateFilter).
// MARKETING_COMMUNICATIONS -- opcional: envio de comunicacoes/novidades. Recusar
//                           nao afeta o servico.
// ALGORITHM_IMPROVEMENT    -- opcional: uso agregado/analitico de dados entre
//                           usuarios para evoluir a formula de matchmaking.
//                           IMPORTANTE: hoje NAO existe nenhum uso desse tipo no
//                           sistema (a formula tem pesos fixos e a reputacao e
//                           calculada por entidade, a partir das reviews da
//                           propria entidade). O consentimento e registrado mas
//                           nao tem efeito pratico nenhum ainda -- e recusa-lo
//                           NAO bloqueia o calculo do proprio score do usuario,
//                           que e execucao de contrato. Ver UserConsentService.
public enum ConsentType {
    TERMS_OF_USE,
    MARKETING_COMMUNICATIONS,
    ALGORITHM_IMPROVEMENT
}
