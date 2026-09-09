package com.main.nexus.service;

import com.main.nexus.dto.ApplyAssessmentTemplateRequestDTO;
import com.main.nexus.dto.AssessmentTemplateQuestionRequestDTO;
import com.main.nexus.dto.AssessmentTemplateQuestionResponseDTO;
import com.main.nexus.dto.AssessmentTemplateRequestDTO;
import com.main.nexus.dto.AssessmentTemplateResponseDTO;
import com.main.nexus.model.AssessmentTemplate;
import com.main.nexus.model.AssessmentTemplateQuestion;
import com.main.nexus.model.Project;
import com.main.nexus.model.ScreeningQuestion;
import com.main.nexus.model.ScreeningQuestionnaire;
import com.main.nexus.model.ScreeningStage;
import com.main.nexus.model.enums.ScreeningQuestionType;
import com.main.nexus.model.enums.ScreeningStageKind;
import com.main.nexus.repository.AssessmentTemplateRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.ScreeningQuestionnaireRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Biblioteca de testes reutilizáveis da empresa (lógica, português, inglês, o que ela quiser) e
// a aplicação de um molde a uma vaga.
//
// O DESENHO EM UMA FRASE: nada aqui mexe no vínculo de execução. ScreeningQuestionnaire continua
// 1:1 com Project, o gate continua sendo o mesmo, o candidato continua respondendo uma
// ScreeningStage comum. O que muda é só a ORIGEM do conteúdo -- em vez de digitado no formulário
// da vaga, ele é copiado de um molde. Execução, prazo, telemetria, correção, decisão, export
// LGPD e card do Kanban seguem exatamente pelo caminho que já existia, sem uma linha de
// mudança.
//
// A alternativa descartada era tornar ScreeningQuestionnaire.project nullable e criar
// "questionários avulsos". Custaria um gatilho de convite novo, um estado de "candidato
// convidado sem vaga", nullable propagado em seis serviços que hoje fazem
// getQuestionnaire().getProject() sem checar, e um caminho separado no export LGPD -- tudo para
// entregar o que a cópia já entrega.
@Service
public class AssessmentTemplateService {

    @Autowired
    private AssessmentTemplateRepository assessmentTemplateRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ScreeningQuestionnaireRepository screeningQuestionnaireRepository;

    @Autowired
    private CompanyAccessService companyAccessService;

    // ── CRUD do molde ──────────────────────────────────────────────────
    //
    // Sem requireOwner em nenhuma operação: molde de teste é conteúdo de recrutamento, mesma
    // régua de ScreeningStage e das notas do Kanban -- qualquer membro ACTIVE cuida. requireOwner
    // fica para o que é da CONTA (cobrança, membros, exclusão).

    @Transactional
    public AssessmentTemplate create(AssessmentTemplateRequestDTO request,
                                     CompanyAccessService.CompanyAccess access) {
        companyAccessService.requireMember(access.role());

        AssessmentTemplate template = new AssessmentTemplate();
        template.setCompany(access.company());
        applyRequest(template, request);
        return assessmentTemplateRepository.save(template);
    }

    @Transactional
    public AssessmentTemplate update(Long id, AssessmentTemplateRequestDTO request,
                                     CompanyAccessService.CompanyAccess access) {
        companyAccessService.requireMember(access.role());

        AssessmentTemplate template = getForCompany(id, access.company().getId());
        applyRequest(template, request);
        return assessmentTemplateRepository.save(template);
    }

    // Aposentar, não apagar. Etapas já geradas continuam apontando pra cá em
    // ScreeningStage.sourceTemplateId, e apagar a linha faria esse rastro virar um id órfão.
    @Transactional
    public AssessmentTemplate setActive(Long id, boolean active,
                                        CompanyAccessService.CompanyAccess access) {
        companyAccessService.requireMember(access.role());

        AssessmentTemplate template = getForCompany(id, access.company().getId());
        template.setActive(active);
        return assessmentTemplateRepository.save(template);
    }

    public List<AssessmentTemplate> listForCompany(Long companyId) {
        return assessmentTemplateRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public List<AssessmentTemplate> listApplicableForCompany(Long companyId) {
        return assessmentTemplateRepository.findByCompanyIdAndActiveTrueOrderByTitleAsc(companyId);
    }

    public AssessmentTemplate getForCompany(Long id, Long companyId) {
        AssessmentTemplate template = assessmentTemplateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Assessment template not found: " + id));
        if (!template.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This assessment template does not belong to your company.");
        }
        return template;
    }

    private void applyRequest(AssessmentTemplate template, AssessmentTemplateRequestDTO request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), "'title' is required.");
        }

        ScreeningStageKind kind = request.kind() != null ? request.kind() : ScreeningStageKind.QUESTIONS;
        if (kind != ScreeningStageKind.QUESTIONS) {
            // BEHAVIORAL é banco fixo de plataforma (a empresa liga ou desliga, não monta) e
            // VIDEO é pergunta sobre a vaga concreta. Nenhum dos dois é "genérico e reaplicável",
            // que é a única coisa que um molde resolve.
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Only question-based templates are supported. Behavioral stages use the "
                  + "fixed platform item bank, and video stages are specific to one opportunity.");
        }
        template.setKind(kind);

        Integer deadline = request.responseDeadlineDays();
        if (deadline == null || deadline <= 0) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "'responseDeadlineDays' must be a positive number.");
        }

        template.setTitle(request.title());
        template.setInstructions(request.instructions());
        template.setResponseDeadlineDays(deadline);
        replaceQuestions(template, request.questions());
    }

    // Substituição total, sem merge por id -- ver AssessmentTemplateQuestion: nada referencia
    // estas linhas, então preservar identidade não compraria nada.
    private void replaceQuestions(
            AssessmentTemplate template, List<AssessmentTemplateQuestionRequestDTO> requested) {

        if (requested == null || requested.isEmpty()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "A template must have at least one question.");
        }

        List<AssessmentTemplateQuestion> merged = new ArrayList<>();
        for (int i = 0; i < requested.size(); i++) {
            AssessmentTemplateQuestion question = new AssessmentTemplateQuestion();
            question.setAssessmentTemplate(template);
            applyQuestionFields(question, requested.get(i), i);
            merged.add(question);
        }

        template.getQuestions().clear();
        template.getQuestions().addAll(merged);
    }

    // Mesmas regras de validação de ScreeningQuestionnaireService.applyQuestionFields -- a
    // questão gerada vai virar uma ScreeningQuestion, então tem que nascer já obedecendo o que
    // aquele lado exige.
    private void applyQuestionFields(
            AssessmentTemplateQuestion question, AssessmentTemplateQuestionRequestDTO request, int orderIndex) {

        if (request.type() == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), "Question 'type' is required.");
        }
        if (request.type() != ScreeningQuestionType.MULTIPLE_CHOICE
                && request.type() != ScreeningQuestionType.ESSAY) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "A template question must be MULTIPLE_CHOICE or ESSAY.");
        }
        if (request.prompt() == null || request.prompt().isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), "Question 'prompt' is required.");
        }

        question.setType(request.type());
        question.setPrompt(request.prompt());
        question.setOrderIndex(orderIndex);

        if (request.type() == ScreeningQuestionType.MULTIPLE_CHOICE) {
            List<String> options = request.options();
            if (options == null || options.size() < 2) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "A MULTIPLE_CHOICE question needs at least 2 options.");
            }
            if (request.correctOptionIndex() == null
                    || request.correctOptionIndex() < 0
                    || request.correctOptionIndex() >= options.size()) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "'correctOptionIndex' must point to a valid option.");
            }
            question.setOptions(new ArrayList<>(options));
            question.setCorrectOptionIndex(request.correctOptionIndex());
        } else {
            question.setOptions(new ArrayList<>());
            question.setCorrectOptionIndex(null);
        }
    }

    // ── aplicar a uma vaga ─────────────────────────────────────────────

    // CÓPIA, NÃO REFERÊNCIA. A etapa gerada é autônoma a partir daqui: editar o molde depois não
    // toca nela, e editar a etapa no formulário da vaga não toca no molde. É o mesmo princípio
    // que mergeStages já aplica ("editar não tem efeito retroativo em quem já respondeu"), agora
    // atravessando a fronteira entre biblioteca e vaga.
    @Transactional
    public ScreeningStage applyToProject(
            Long templateId, ApplyAssessmentTemplateRequestDTO request,
            CompanyAccessService.CompanyAccess access) {

        companyAccessService.requireMember(access.role());
        Long companyId = access.company().getId();

        AssessmentTemplate template = getForCompany(templateId, companyId);
        if (!Boolean.TRUE.equals(template.getActive())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "This assessment template has been retired and cannot be applied.");
        }
        if (request.projectId() == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), "'projectId' is required.");
        }

        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found: " + request.projectId()));

        // Guard do enunciado: o molde só se aplica a uma vaga da MESMA empresa. Os dois lados são
        // checados contra a empresa logada, não um contra o outro -- assim nem um molde de outra
        // empresa nem uma vaga de outra empresa passam, mesmo que combinassem entre si.
        if (!project.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This project does not belong to your company.");
        }

        ScreeningQuestionnaire questionnaire = questionnaireFor(project);

        ScreeningStage stage = new ScreeningStage();
        stage.setScreeningQuestionnaire(questionnaire);
        stage.setKind(ScreeningStageKind.QUESTIONS);
        stage.setSourceTemplateId(template.getId());
        stage.setTitle(request.title() != null && !request.title().isBlank()
                ? request.title() : template.getTitle());
        stage.setInstructions(template.getInstructions());
        stage.setResponseDeadlineDays(
                request.responseDeadlineDays() != null && request.responseDeadlineDays() > 0
                        ? request.responseDeadlineDays() : template.getResponseDeadlineDays());
        stage.setActive(true);
        // Entra no fim da fila. max+1 em vez de size(): etapas desativadas continuam na lista com
        // o orderIndex que tinham, e size() poderia colidir com uma delas.
        stage.setOrderIndex(nextOrderIndex(questionnaire));

        for (AssessmentTemplateQuestion source : template.getQuestions()) {
            ScreeningQuestion question = new ScreeningQuestion();
            question.setScreeningStage(stage);
            question.setType(source.getType());
            question.setPrompt(source.getPrompt());
            question.setOrderIndex(source.getOrderIndex());
            question.setActive(true);
            // Cópia defensiva da lista: sem isto as duas entidades compartilhariam a mesma
            // instância de List, e editar as alternativas da etapa mexeria no molde.
            question.setOptions(new ArrayList<>(source.getOptions()));
            question.setCorrectOptionIndex(source.getCorrectOptionIndex());
            stage.getQuestions().add(question);
        }

        questionnaire.getStages().add(stage);
        screeningQuestionnaireRepository.save(questionnaire);
        return stage;
    }

    // A vaga pode ainda não ter processo seletivo -- é o caso mais comum de quem quer aplicar um
    // teste pronto. Criar o questionário aqui, com um título padrão editável no formulário da
    // vaga, é preferível a recusar com "crie o processo seletivo primeiro", que obrigaria a
    // empresa a inventar uma etapa só pra poder aplicar a que ela realmente queria.
    private ScreeningQuestionnaire questionnaireFor(Project project) {
        return screeningQuestionnaireRepository.findByProjectId(project.getId())
                .orElseGet(() -> {
                    ScreeningQuestionnaire created = new ScreeningQuestionnaire();
                    created.setProject(project);
                    created.setTitle("Processo seletivo — " + project.getTitle());
                    return created;
                });
    }

    private int nextOrderIndex(ScreeningQuestionnaire questionnaire) {
        int max = -1;
        for (ScreeningStage stage : questionnaire.getStages()) {
            if (stage.getOrderIndex() != null && stage.getOrderIndex() > max) {
                max = stage.getOrderIndex();
            }
        }
        return max + 1;
    }

    // ── DTO ────────────────────────────────────────────────────────────

    public AssessmentTemplateResponseDTO toResponseDTO(AssessmentTemplate template) {
        List<AssessmentTemplateQuestionResponseDTO> questions = template.getQuestions().stream()
                .map(q -> new AssessmentTemplateQuestionResponseDTO(
                        q.getId(), q.getType(), q.getPrompt(), q.getOptions(), q.getCorrectOptionIndex()))
                .toList();

        return new AssessmentTemplateResponseDTO(
                template.getId(),
                template.getTitle(),
                template.getKind(),
                template.getInstructions(),
                template.getResponseDeadlineDays(),
                template.getActive(),
                template.getCreatedAt(),
                questions);
    }

}
