package com.main.nexus.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Processo seletivo em etapas, criado pelo contratante e vinculado 1:1 a um Project -- uma ou
// mais ScreeningStage em sequência, cada uma com suas próprias perguntas (qualificatórias,
// técnicas, ou qualquer outra coisa que valha perguntar antes de avançar). O profissional avança
// uma etapa de cada vez; a empresa decide manualmente, etapa por etapa, se ele passa pra próxima
// (ver ScreeningInvitationService.approveStage/reproveStage). Vale tanto para PROJECT quanto para
// JOB, diferente de Proposal, que é exclusivo de PROJECT (ver ProjectService.validateByType) --
// por isso não tem nenhuma flag de gate em Project. Sem trava de edição -- pode editar/remover
// etapas a qualquer momento, sem efeito retroativo em quem já respondeu (ver ScreeningStage).
@Entity
@Table(name = "tb_screening_questionnaire")
public class ScreeningQuestionnaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 1:1 com Project -- uma vaga tem no máximo um questionário (decisão confirmada com o
    // usuário, diferente da v1, que permitia vários reaproveitáveis por vaga).
    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Ordenado por ScreeningStage.orderIndex (setado por ScreeningQuestionnaireService) --
    // @OrderBy em vez de @OrderColumn porque essa é uma relação mappedBy (bidirecional);
    // @OrderColumn é para coleções cujo dono é o lado "many" via chave estrangeira própria, não
    // este caso.
    @OneToMany(mappedBy = "screeningQuestionnaire", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @BatchSize(size = 50)
    private List<ScreeningStage> stages = new ArrayList<>();

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<ScreeningStage> getStages() {
        return stages;
    }

    public void setStages(List<ScreeningStage> stages) {
        this.stages = stages != null ? stages : new ArrayList<>();
    }
}
