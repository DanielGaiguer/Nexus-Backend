package com.main.nexus.dto;

import java.util.List;

// A escala Likert de 5 pontos dos itens comportamentais, em UM lugar só. É fixa e igual pra todo
// item, por isso não é guardada em ScreeningQuestion.options (ver ScreeningQuestionType).
//
// O índice é o que trafega e o que fica em ScreeningAnswer.selectedOptionIndex (0..4); o rótulo
// existe porque o export LGPD entrega JSON direto ao titular, sem passar pelo front -- "3" ali
// não significaria nada pra quem lê.
public final class ScreeningLikertScale {

    public static final List<String> LABELS = List.of(
            "Discordo totalmente",
            "Discordo",
            "Neutro",
            "Concordo",
            "Concordo totalmente");

    public static String labelFor(Integer index) {
        if (index == null || index < 0 || index >= LABELS.size()) {
            return null;
        }
        return LABELS.get(index);
    }

    private ScreeningLikertScale() {}
}
