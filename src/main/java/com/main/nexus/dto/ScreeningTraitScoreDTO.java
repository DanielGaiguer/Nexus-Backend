package com.main.nexus.dto;

import com.main.nexus.model.enums.BigFiveDimension;

// Uma dimensão do perfil. Nunca circula sozinho -- só dentro de ScreeningTraitProfileDTO, que é
// quem carrega o aviso obrigatório.
//
// `score` é 0-100 dentro da escala do próprio instrumento, NÃO um percentil populacional: não há
// amostra normativa brasileira por trás. `answeredItemCount` acompanha porque um score apurado
// sobre 10 itens e um apurado sobre 2 não têm o mesmo peso, e sem ele não dá pra distinguir os
// dois depois.
public record ScreeningTraitScoreDTO(
        BigFiveDimension dimension,
        String label,
        Double score,
        Integer answeredItemCount
) {}
