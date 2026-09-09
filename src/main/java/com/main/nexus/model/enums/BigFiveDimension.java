package com.main.nexus.model.enums;

// As cinco dimensões do modelo Big Five (OCEAN), usadas por BehavioralItem (o item pertence a
// uma dimensão) e por ScreeningTraitScore (o resultado agregado por dimensão).
//
// ATENÇÃO À POLARIDADE DE NEUROTICISM: os itens do IPIP-50 (Goldberg, 1992) são publicados
// chaveados como "Emotional Stability" -- o oposto de Neuroticismo. Como este enum nomeia a
// dimensão NEUROTICISM, os itens desse fator entram com a polaridade INVERTIDA em relação à
// fonte: "Fico estressado com facilidade", que é reverso para Estabilidade Emocional, é DIRETO
// para Neuroticismo. Ver BehavioralItemSeed, onde isso está aplicado item a item. Trocar o nome
// desta constante sem reverter `reverseScored` daquele fator inverte silenciosamente o
// significado de uma dimensão inteira.
public enum BigFiveDimension {
    OPENNESS,
    CONSCIENTIOUSNESS,
    EXTRAVERSION,
    AGREEABLENESS,
    NEUROTICISM;

    // Rótulo pt-BR exibido -- vive aqui, e não no front, porque o mesmo texto é usado no export
    // LGPD (JSON entregue ao titular), que não passa pelo front.
    public String label() {
        return switch (this) {
            case OPENNESS -> "Abertura a experiências";
            case CONSCIENTIOUSNESS -> "Conscienciosidade";
            case EXTRAVERSION -> "Extroversão";
            case AGREEABLENESS -> "Amabilidade";
            case NEUROTICISM -> "Neuroticismo";
        };
    }
}
