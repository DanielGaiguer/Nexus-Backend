package com.main.nexus.dto;

import com.main.nexus.model.ScreeningTraitScore;
import java.util.Comparator;
import java.util.List;

// Resultado de uma etapa BEHAVIORAL: o perfil de traços inteiro, com o aviso obrigatório
// GRUDADO nele.
//
// O aviso é campo DESTE record, e não de quem contém o perfil, de propósito: assim é
// estruturalmente impossível uma resposta da API devolver um ScreeningTraitScore sem o aviso
// junto. Um endpoint novo que sirva o perfil não tem como esquecer -- ele não consegue serializar
// os números separados do texto.
public record ScreeningTraitProfileDTO(
        List<ScreeningTraitScoreDTO> scores,
        String disclaimer
) {

    public static final String DISCLAIMER =
            "Resultado autodeclarado, indicativo — não é laudo psicológico nem substitui "
          + "avaliação de profissional de RH/Psicologia.";

    // null (e não um perfil vazio) quando a tentativa não é de etapa comportamental -- o front
    // usa a ausência do campo pra decidir se desenha a seção de perfil.
    public static ScreeningTraitProfileDTO from(List<ScreeningTraitScore> traitScores) {
        if (traitScores == null || traitScores.isEmpty()) {
            return null;
        }
        List<ScreeningTraitScoreDTO> scores = traitScores.stream()
                // Ordem estável (a do enum), não a ordem em que o Hibernate devolveu -- o
                // gráfico de radar do front ficaria com os eixos dançando entre candidatos.
                .sorted(Comparator.comparing(t -> t.getDimension().ordinal()))
                .map(t -> new ScreeningTraitScoreDTO(
                        t.getDimension(),
                        t.getDimension().label(),
                        t.getScore(),
                        t.getAnsweredItemCount()))
                .toList();
        return new ScreeningTraitProfileDTO(scores, DISCLAIMER);
    }
}
