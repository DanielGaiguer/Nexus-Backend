package com.main.nexus.model;

import com.main.nexus.model.enums.BigFiveDimension;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Um item do inventário Big Five. BANCO DE ITENS DE PLATAFORMA, FIXO: semeado uma única vez por
// BehavioralItemSeed a partir do IPIP-50 (International Personality Item Pool, domínio público),
// nunca escrito por empresa. Decisão de produto confirmada com o usuário -- um instrumento
// psicométrico só se sustenta (e só se defende como "indicativo, não laudo") se o conjunto de
// itens for fixo e conhecido; deixar cada recrutador inventar as próprias perguntas
// "comportamentais" produziria um número com aparência de medida e nenhuma medida por trás.
//
// Não tem FK para Company nem para Project -- é conteúdo da plataforma. Quando uma empresa liga
// uma etapa BEHAVIORAL numa vaga, os itens ativos são COPIADOS para ScreeningQuestion daquela
// etapa (ver ScreeningQuestionnaireService.populateBehavioralQuestions). Cópia, e não
// referência, pelo mesmo motivo que o resto do módulo de triagem já adota: mexer no banco de
// itens depois não pode alterar retroativamente um teste que alguém já respondeu.
@Entity
@Table(name = "tb_behavioral_item")
public class BehavioralItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BigFiveDimension dimension;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    // Item cuja concordância PUXA A DIMENSÃO PRA BAIXO -- pontuado como (6 - resposta). Ver a
    // nota de polaridade em BigFiveDimension antes de mexer nos itens de NEUROTICISM.
    @Column(nullable = false)
    private Boolean reverseScored = false;

    // Ordem de exibição dentro da etapa. Os itens são semeados intercalando as dimensões, e não
    // agrupados por fator, pra reduzir o efeito de o respondente perceber o padrão e responder
    // "em bloco".
    @Column(nullable = false)
    private Integer orderIndex = 0;

    // Permite aposentar um item sem apagar a linha (etapas já criadas mantêm a cópia que
    // receberam; só as próximas deixam de recebê-lo).
    @Column(nullable = false)
    private Boolean active = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigFiveDimension getDimension() {
        return dimension;
    }

    public void setDimension(BigFiveDimension dimension) {
        this.dimension = dimension;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Boolean getReverseScored() {
        return reverseScored;
    }

    public void setReverseScored(Boolean reverseScored) {
        this.reverseScored = reverseScored;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
