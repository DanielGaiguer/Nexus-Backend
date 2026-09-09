package com.main.nexus.model;

import com.main.nexus.model.enums.BigFiveDimension;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// O resultado de UMA dimensão do Big Five numa tentativa de etapa BEHAVIORAL. Uma linha por
// dimensão respondida (cinco, na prática) -- é isto que faz o resultado ser um PERFIL DE TRAÇOS
// e não uma nota: ScreeningInvitation.autoScorePercent continua existindo pras etapas
// QUESTIONS, mas fica null numa etapa comportamental, porque ali não há resposta certa pra
// contar.
//
// `score` é 0-100 normalizado pela quantidade de itens efetivamente respondidos naquela dimensão
// (ver ScreeningInvitationService.computeTraitScores) -- 0 = concordou com tudo que puxa a
// dimensão pra baixo, 100 = o oposto. NÃO é percentil populacional: não existe amostra normativa
// brasileira por trás disto, é a posição bruta dentro da escala. Ver o aviso obrigatório em
// ScreeningTraitProfileDTO, que acompanha estes números em toda resposta da API.
@Entity
@Table(name = "tb_screening_trait_score",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_screening_trait_score_invitation_dimension",
               columnNames = {"screening_invitation_id", "dimension"}))
public class ScreeningTraitScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "screening_invitation_id", nullable = false)
    private ScreeningInvitation screeningInvitation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BigFiveDimension dimension;

    @Column(nullable = false)
    private Double score;

    // Quantos itens daquela dimensão o candidato de fato respondeu -- guardado junto porque um
    // score de 0-100 calculado sobre 2 itens e um calculado sobre 10 não valem a mesma coisa, e
    // sem isto não dá pra saber depois qual dos dois se está lendo.
    @Column(nullable = false)
    private Integer answeredItemCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ScreeningInvitation getScreeningInvitation() {
        return screeningInvitation;
    }

    public void setScreeningInvitation(ScreeningInvitation screeningInvitation) {
        this.screeningInvitation = screeningInvitation;
    }

    public BigFiveDimension getDimension() {
        return dimension;
    }

    public void setDimension(BigFiveDimension dimension) {
        this.dimension = dimension;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Integer getAnsweredItemCount() {
        return answeredItemCount;
    }

    public void setAnsweredItemCount(Integer answeredItemCount) {
        this.answeredItemCount = answeredItemCount;
    }
}
