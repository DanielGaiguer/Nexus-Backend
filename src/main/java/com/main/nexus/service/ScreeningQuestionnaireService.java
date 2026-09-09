package com.main.nexus.service;

import com.main.nexus.dto.ScreeningQuestionRequestDTO;
import com.main.nexus.dto.ScreeningQuestionResponseDTO;
import com.main.nexus.dto.ScreeningQuestionnaireRequestDTO;
import com.main.nexus.dto.ScreeningQuestionnaireResponseDTO;
import com.main.nexus.dto.ScreeningStageRequestDTO;
import com.main.nexus.dto.ScreeningStageResponseDTO;
import com.main.nexus.model.BehavioralItem;
import com.main.nexus.model.Project;
import com.main.nexus.model.ScreeningQuestion;
import com.main.nexus.model.ScreeningQuestionnaire;
import com.main.nexus.model.ScreeningStage;
import com.main.nexus.model.enums.ScreeningQuestionType;
import com.main.nexus.model.enums.ScreeningStageKind;
import com.main.nexus.repository.BehavioralItemRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.ScreeningInvitationRepository;
import com.main.nexus.repository.ScreeningQuestionnaireRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Processo seletivo em etapas: um ScreeningQuestionnaire por Project (opcional), criado/editado
// pelo contratante junto com o cadastro/edição da vaga. Sem trava de edição -- editar/remover
// etapas e questões é sempre permitido; o que já tem candidato em andamento (invitation/answer
// existente) vira `active=false` em vez de apagado de verdade, pra não afetar retroativamente
// quem já respondeu (ver mergeStages/mergeQuestions). O envio a candidatos não é uma ação manual
// (ver ScreeningInvitationService.checkGate) -- esse service só cuida do template em si.
@Service
public class ScreeningQuestionnaireService {

    @Autowired
    private ScreeningQuestionnaireRepository screeningQuestionnaireRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ScreeningInvitationRepository screeningInvitationRepository;

    @Autowired
    private BehavioralItemRepository behavioralItemRepository;

    @Transactional
    public ScreeningQuestionnaire create(ScreeningQuestionnaireRequestDTO request, Long companyId) {
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found"));

        if (!project.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This project does not belong to your company.");
        }
        if (screeningQuestionnaireRepository.findByProjectId(project.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "This project already has a screening process. Edit the existing one instead.");
        }

        ScreeningQuestionnaire questionnaire = new ScreeningQuestionnaire();
        questionnaire.setProject(project);
        applyRequest(questionnaire, request);

        return screeningQuestionnaireRepository.save(questionnaire);
    }

    @Transactional
    public ScreeningQuestionnaire update(Long id, ScreeningQuestionnaireRequestDTO request, Long companyId) {
        ScreeningQuestionnaire questionnaire = getForCompany(id, companyId);
        applyRequest(questionnaire, request);
        return screeningQuestionnaireRepository.save(questionnaire);
    }

    private void applyRequest(ScreeningQuestionnaire questionnaire, ScreeningQuestionnaireRequestDTO request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), "'title' is required.");
        }
        questionnaire.setTitle(request.title());
        questionnaire.setInstructions(request.instructions());
        mergeStages(questionnaire, request.stages());
    }

    // Casa cada etapa da requisição com uma existente (por id) ou cria uma nova; etapas
    // existentes que não vieram na requisição são removidas de verdade (se nunca respondidas) ou
    // desativadas (se já têm invitation -- soft-delete, sem efeito retroativo).
    private void mergeStages(ScreeningQuestionnaire questionnaire, List<ScreeningStageRequestDTO> requestedStages) {
        if (requestedStages == null || requestedStages.isEmpty()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "A screening process must have at least one stage.");
        }

        Map<Long, ScreeningStage> existingById = new HashMap<>();
        for (ScreeningStage s : questionnaire.getStages()) {
            if (s.getId() != null) {
                existingById.put(s.getId(), s);
            }
        }

        List<ScreeningStage> merged = new ArrayList<>();
        Set<Long> keptIds = new HashSet<>();

        for (int i = 0; i < requestedStages.size(); i++) {
            ScreeningStageRequestDTO req = requestedStages.get(i);
            ScreeningStage stage;
            if (req.id() != null) {
                stage = existingById.get(req.id());
                if (stage == null) {
                    throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                            "Stage " + req.id() + " does not belong to this screening process.");
                }
                keptIds.add(stage.getId());
            } else {
                stage = new ScreeningStage();
                stage.setScreeningQuestionnaire(questionnaire);
                // Procedência só na criação -- editar a etapa depois não reescreve de onde ela
                // veio (ver ScreeningStage.sourceTemplateId).
                stage.setSourceTemplateId(req.sourceTemplateId());
            }

            if (req.title() == null || req.title().isBlank()) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400), "Stage 'title' is required.");
            }
            if (req.responseDeadlineDays() == null || req.responseDeadlineDays() <= 0) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Stage 'responseDeadlineDays' must be a positive number.");
            }

            ScreeningStageKind requestedKind =
                    req.kind() != null ? req.kind() : ScreeningStageKind.QUESTIONS;
            // O tipo e definido na criacao e nunca muda: trocar o tipo de uma etapa que ja tem
            // gente respondendo trocaria o instrumento debaixo dela, e as respostas antigas
            // (que continuam apontando pras questoes antigas) passariam a ser lidas com outra
            // regua. Editar titulo/instrucoes/prazo continua livre, como sempre foi.
            if (stage.getId() != null && stage.getKind() != requestedKind) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "A stage type cannot be changed after the stage is created. "
                      + "Remove the stage and add a new one instead.");
            }
            stage.setKind(requestedKind);

            stage.setOrderIndex(i);
            stage.setTitle(req.title());
            stage.setInstructions(req.instructions());
            stage.setResponseDeadlineDays(req.responseDeadlineDays());
            stage.setActive(true);

            if (requestedKind == ScreeningStageKind.BEHAVIORAL) {
                populateBehavioralQuestions(stage, req.questions());
            } else {
                mergeQuestions(stage, req.questions());
            }

            merged.add(stage);
        }

        for (ScreeningStage existing : questionnaire.getStages()) {
            if (existing.getId() != null && !keptIds.contains(existing.getId())) {
                if (screeningInvitationRepository.existsByScreeningStageId(existing.getId())) {
                    existing.setActive(false);
                    merged.add(existing);
                }
                // senão: some da lista -> orphanRemoval apaga de verdade, nunca foi respondida.
            }
        }

        questionnaire.getStages().clear();
        questionnaire.getStages().addAll(merged);
    }

    // Etapa BEHAVIORAL nao tem edicao de conteudo: os itens vem do banco fixo de plataforma
    // (BehavioralItem, semeado por BehavioralItemSeed) e sao COPIADOS pra ca uma unica vez, na
    // criacao da etapa. A empresa so liga ou desliga a etapa -- decisao de produto confirmada
    // com o usuario: um inventario psicometrico so se sustenta se o conjunto de itens for fixo.
    //
    // Copia e nao referencia, e so na primeira vez: uma etapa que ja tem itens NUNCA e
    // repopulada, senao salvar a vaga de novo depois de um item ser aposentado do banco mudaria
    // o instrumento debaixo de quem esta respondendo. Mesmo espirito do resto do modulo (editar
    // nao tem efeito retroativo).
    private void populateBehavioralQuestions(
            ScreeningStage stage, List<ScreeningQuestionRequestDTO> requestedQuestions) {

        // Defesa no backend, independente do que o editor ofereca (o front do Prompt 4/7 nem
        // mostra a opcao): questao customizada numa etapa comportamental e rejeitada.
        if (requestedQuestions != null && !requestedQuestions.isEmpty()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "A behavioral stage uses a fixed platform question set and does not "
                  + "accept custom questions.");
        }

        if (!stage.getQuestions().isEmpty()) {
            return;
        }

        List<BehavioralItem> items = behavioralItemRepository.findByActiveTrueOrderByOrderIndexAsc();
        if (items.isEmpty()) {
            // Banco de itens nao semeado (ver BehavioralItemSeed). Falhar aqui e melhor que
            // criar uma etapa comportamental vazia, que so apareceria como bug pro candidato.
            throw new ResponseStatusException(HttpStatusCode.valueOf(503),
                    "The behavioral assessment item bank is not available. Try again later.");
        }

        for (int i = 0; i < items.size(); i++) {
            BehavioralItem item = items.get(i);
            ScreeningQuestion question = new ScreeningQuestion();
            question.setScreeningStage(stage);
            question.setType(ScreeningQuestionType.LIKERT_SCALE);
            question.setPrompt(item.getPrompt());
            question.setTraitDimension(item.getDimension());
            question.setReverseScored(item.getReverseScored());
            question.setOrderIndex(i);
            question.setActive(true);
            // Sem gabarito: nao existe resposta certa num inventario de personalidade.
            question.setOptions(new ArrayList<>());
            question.setCorrectOptionIndex(null);
            stage.getQuestions().add(question);
        }
    }

    // Mesmo raciocínio de mergeStages, um nível abaixo: casa por id, remove de verdade só quem
    // nunca foi respondida.
    private void mergeQuestions(ScreeningStage stage, List<ScreeningQuestionRequestDTO> requestedQuestions) {
        if (requestedQuestions == null || requestedQuestions.isEmpty()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Each stage must have at least one question.");
        }

        Map<Long, ScreeningQuestion> existingById = new HashMap<>();
        for (ScreeningQuestion q : stage.getQuestions()) {
            if (q.getId() != null) {
                existingById.put(q.getId(), q);
            }
        }

        List<ScreeningQuestion> merged = new ArrayList<>();
        Set<Long> keptIds = new HashSet<>();

        for (int i = 0; i < requestedQuestions.size(); i++) {
            ScreeningQuestionRequestDTO req = requestedQuestions.get(i);
            ScreeningQuestion question;
            if (req.id() != null) {
                question = existingById.get(req.id());
                if (question == null) {
                    throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                            "Question " + req.id() + " does not belong to this stage.");
                }
                keptIds.add(question.getId());
            } else {
                question = new ScreeningQuestion();
                question.setScreeningStage(stage);
            }

            assertTypeMatchesStage(stage, req.type());
            applyQuestionFields(question, req, i);
            question.setActive(true);
            merged.add(question);
        }

        for (ScreeningQuestion existing : stage.getQuestions()) {
            if (existing.getId() != null && !keptIds.contains(existing.getId())
                    && questionHasAnswers(existing)) {
                existing.setActive(false);
                merged.add(existing);
            }
        }

        stage.getQuestions().clear();
        stage.getQuestions().addAll(merged);
    }

    // Sem repositório próprio de ScreeningAnswer aqui de propósito -- uma questão só é
    // referenciada por uma answer depois que a invitation da etapa foi submetida, e a etapa já
    // teria pelo menos uma invitation nesse caso (checagem mais barata, mesmo resultado prático).
    private boolean questionHasAnswers(ScreeningQuestion question) {
        return question.getScreeningStage().getId() != null
                && screeningInvitationRepository.existsByScreeningStageId(question.getScreeningStage().getId());
    }

    // Etapa homogênea: o tipo da questão tem que casar com o tipo da etapa. Sem isto, a
    // empresa poderia misturar uma pergunta de vídeo com duas múltiplas escolhas na mesma etapa,
    // e a tela de resposta (que decide a UI inteira por ScreeningStage.kind) não teria como
    // renderizar as duas coisas. Vale pros dois lados -- VIDEO_RESPONSE só em etapa VIDEO, e
    // etapa VIDEO só aceita VIDEO_RESPONSE.
    private void assertTypeMatchesStage(ScreeningStage stage, ScreeningQuestionType type) {
        boolean videoStage = stage.getKind() == ScreeningStageKind.VIDEO;
        boolean videoQuestion = type == ScreeningQuestionType.VIDEO_RESPONSE;

        if (videoStage && !videoQuestion) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "A video stage only accepts video questions.");
        }
        if (!videoStage && videoQuestion) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "A video question can only be used in a video stage.");
        }
    }

    private void applyQuestionFields(ScreeningQuestion question, ScreeningQuestionRequestDTO request, int orderIndex) {
        if (request.type() == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), "Question 'type' is required.");
        }
        // LIKERT_SCALE so existe dentro de etapa BEHAVIORAL, e la os itens vem do banco fixo --
        // nunca por este caminho, que e o do conteudo escrito pela empresa.
        if (request.type() == ScreeningQuestionType.LIKERT_SCALE) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Likert items belong to a behavioral stage and cannot be created manually.");
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
            question.setOptions(options);
            question.setCorrectOptionIndex(request.correctOptionIndex());
        } else {
            // ESSAY e VIDEO_RESPONSE não usam gabarito -- a empresa lê o texto ou assiste ao
            // vídeo e decide (ver ScreeningAnswer).
            question.setOptions(new ArrayList<>());
            question.setCorrectOptionIndex(null);
        }
    }

    // 1:1 com Project -- devolve o único questionário da vaga, se existir.
    public Optional<ScreeningQuestionnaire> getByProject(Long projectId, Long companyId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found"));
        if (!project.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This project does not belong to your company.");
        }
        return screeningQuestionnaireRepository.findByProjectId(projectId);
    }

    public ScreeningQuestionnaire findById(Long id) {
        return screeningQuestionnaireRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Screening questionnaire not found: " + id));
    }

    public ScreeningQuestionnaire getForCompany(Long id, Long companyId) {
        ScreeningQuestionnaire questionnaire = findById(id);
        if (!questionnaire.getProject().getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This questionnaire does not belong to your company.");
        }
        return questionnaire;
    }

    public ScreeningQuestionnaireResponseDTO toResponseDTO(ScreeningQuestionnaire questionnaire) {
        // Etapa removida (active=false) não deve reaparecer aqui -- este DTO alimenta o
        // formulário de edição da vaga, e devolvê-la faria a etapa "voltar" pro formulário; se a
        // empresa salvasse de novo sem notar, mergeStages reativaria ela (active=true
        // incondicional pra qualquer etapa presente no request). Mesmo filtro que
        // toStageResponseDTO já aplica nas questões.
        List<ScreeningStageResponseDTO> stages = questionnaire.getStages().stream()
                .filter(ScreeningStage::getActive)
                .map(this::toStageResponseDTO)
                .toList();

        return new ScreeningQuestionnaireResponseDTO(
                questionnaire.getId(),
                questionnaire.getProject().getId(),
                questionnaire.getProject().getTitle(),
                questionnaire.getTitle(),
                questionnaire.getInstructions(),
                questionnaire.getCreatedAt(),
                stages
        );
    }

    // Público desde o Prompt 3/7: AssessmentTemplateController devolve a etapa recém-gerada
    // por um molde, e ela tem que sair no MESMO formato que o formulário da vaga já consome.
    public ScreeningStageResponseDTO toStageResponseDTO(ScreeningStage stage) {
        long activeCount = stage.getQuestions().stream()
                .filter(ScreeningQuestion::getActive)
                .count();

        // Etapa comportamental devolve a lista VAZIA pro formulario da vaga: os itens nao sao
        // editaveis, e devolve-los faria o form fazer round-trip de 50 questoes que ele nem
        // deveria conhecer. `behavioralItemCount` e o que a tela precisa exibir.
        List<ScreeningQuestionResponseDTO> questions = stage.isBehavioral()
                ? List.of()
                : stage.getQuestions().stream()
                        .filter(ScreeningQuestion::getActive)
                        .map(q -> new ScreeningQuestionResponseDTO(
                                q.getId(), q.getType(), q.getPrompt(),
                                q.getOptions(), q.getCorrectOptionIndex()))
                        .toList();

        return new ScreeningStageResponseDTO(
                stage.getId(),
                stage.getKind(),
                stage.getOrderIndex(),
                stage.getTitle(),
                stage.getInstructions(),
                stage.getResponseDeadlineDays(),
                stage.getActive(),
                questions,
                stage.isBehavioral() ? (int) activeCount : null
        );
    }
}
