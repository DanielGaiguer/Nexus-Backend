package com.main.nexus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// Uma coluna intermediária do Kanban de contratação de uma vaga -- "Triagem", "Entrevista", etc.
// Configurável por Project (mesmo espírito do ScreeningStage: name/orderIndex/active), gerida pelo
// contratante e reordenável. As colunas terminais do board ("Contratado"/"Reprovado") NÃO são
// PipelineStage -- são derivadas em tempo de leitura a partir do estado real do Match
// (ver PipelineService.deriveColumn), justamente porque não são posições arrastáveis.
//
// `active=false` = coluna arquivada: um card que já estava nela continua sendo exibido ali (com
// indicador "etapa arquivada") e pode ser arrastado pra fora, mas ela deixa de ser um destino
// válido de drop -- mesma regra que ScreeningStage usa para active=false. A primeira leitura do
// board de uma vaga sem nenhuma PipelineStage semeia 5 etapas default
// (ver PipelineService.ensureStages), no mesmo padrão lazy de CommissionService.getPolicy().
@Entity
@Table(name = "tb_pipeline_stage")
public class PipelineStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 120)
    private String name;

    // Posição da coluna no board -- setado por PipelineService ao (re)construir a lista, igual ao
    // orderIndex de ScreeningStage.
    @Column(nullable = false)
    private Integer orderIndex = 0;

    @Column(nullable = false)
    private Boolean active = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
