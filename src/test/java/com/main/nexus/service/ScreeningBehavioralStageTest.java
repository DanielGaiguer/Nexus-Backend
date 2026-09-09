package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.main.nexus.dto.ScreeningAnswerSubmitDTO;
import com.main.nexus.dto.ScreeningInvitationDetailDTO;
import com.main.nexus.dto.ScreeningQuestionRequestDTO;
import com.main.nexus.dto.ScreeningQuestionnaireRequestDTO;
import com.main.nexus.dto.ScreeningStageRequestDTO;
import com.main.nexus.dto.ScreeningSubmissionRequestDTO;
import com.main.nexus.dto.ScreeningTraitProfileDTO;
import com.main.nexus.dto.ScreeningTraitScoreDTO;
import com.main.nexus.model.BehavioralItem;
import com.main.nexus.model.Company;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.ScreeningInvitation;
import com.main.nexus.model.ScreeningQuestion;
import com.main.nexus.model.ScreeningQuestionnaire;
import com.main.nexus.model.ScreeningStage;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.BigFiveDimension;
import com.main.nexus.model.enums.ScreeningInvitationStatus;
import com.main.nexus.model.enums.ScreeningQuestionType;
import com.main.nexus.model.enums.ScreeningStageKind;
import com.main.nexus.repository.BehavioralItemRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.ScreeningInvitationRepository;
import com.main.nexus.repository.ScreeningQuestionnaireRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

// Cobre o que o Prompt 1/7 introduziu no caminho de submissão -- que até aqui não tinha nenhum
// teste. Duas frentes:
//  - ScreeningInvitationService.submit numa etapa BEHAVIORAL: pontuação por dimensão, item
//    reverso, e a regra de que a etapa NUNCA reprova e nunca espera decisão da empresa.
//  - ScreeningQuestionnaireService: a etapa comportamental é montada a partir do banco fixo de
//    plataforma e recusa qualquer pergunta escrita pela empresa.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScreeningBehavioralStageTest {

    private static final Long COMPANY_ID = 10L;
    private static final Long PROJECT_ID = 100L;
    private static final Long PROFESSIONAL_ID = 55L;
    private static final Long INVITATION_ID = 900L;

    @Mock private ScreeningInvitationRepository screeningInvitationRepository;
    @Mock private ScreeningQuestionnaireRepository screeningQuestionnaireRepository;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;
    @Mock private CompanyAccessService companyAccessService;

    @InjectMocks private ScreeningInvitationService invitationService;

    @Mock private ProjectRepository projectRepository;
    @Mock private BehavioralItemRepository behavioralItemRepository;

    @InjectMocks private ScreeningQuestionnaireService questionnaireService;

    private Company company;
    private Project project;
    private Professional professional;
    private ScreeningQuestionnaire questionnaire;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(COMPANY_ID);
        company.setCompanyName("Empresa Teste");

        project = new Project();
        project.setId(PROJECT_ID);
        project.setTitle("Vaga Teste");
        project.setCompany(company);

        User professionalUser = new User();
        professionalUser.setId(7L);
        professionalUser.setEmail("pro@teste.com");

        professional = new Professional();
        professional.setId(PROFESSIONAL_ID);
        professional.setName("Candidato Teste");
        professional.setUser(professionalUser);

        questionnaire = new ScreeningQuestionnaire();
        questionnaire.setId(1L);
        questionnaire.setProject(project);
        questionnaire.setTitle("Processo Teste");

        when(companyAccessService.operationalRecipients(any())).thenReturn(List.of());
        when(screeningInvitationRepository.save(any(ScreeningInvitation.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(screeningInvitationRepository
                .findByScreeningStageScreeningQuestionnaireProjectIdAndProfessionalId(anyLong(), anyLong()))
                .thenReturn(List.of());
    }

    // ── helpers ────────────────────────────────────────────────────────

    private ScreeningStage behavioralStage(Long id) {
        ScreeningStage stage = new ScreeningStage();
        stage.setId(id);
        stage.setKind(ScreeningStageKind.BEHAVIORAL);
        stage.setTitle("Perfil comportamental");
        stage.setOrderIndex(questionnaire.getStages().size());
        stage.setResponseDeadlineDays(3);
        stage.setActive(true);
        stage.setScreeningQuestionnaire(questionnaire);
        questionnaire.getStages().add(stage);
        return stage;
    }

    private ScreeningQuestion likertItem(
            ScreeningStage stage, Long id, BigFiveDimension dimension, boolean reverse) {
        ScreeningQuestion question = new ScreeningQuestion();
        question.setId(id);
        question.setScreeningStage(stage);
        question.setType(ScreeningQuestionType.LIKERT_SCALE);
        question.setPrompt("Item " + id);
        question.setTraitDimension(dimension);
        question.setReverseScored(reverse);
        question.setOrderIndex(stage.getQuestions().size());
        question.setActive(true);
        stage.getQuestions().add(question);
        return question;
    }

    private ScreeningInvitation invitationFor(ScreeningStage stage) {
        ScreeningInvitation invitation = new ScreeningInvitation();
        invitation.setId(INVITATION_ID);
        invitation.setScreeningStage(stage);
        invitation.setProfessional(professional);
        invitation.setStatus(ScreeningInvitationStatus.IN_PROGRESS);
        invitation.setSentAt(LocalDateTime.now().minusDays(1));
        invitation.setDeadlineAt(LocalDateTime.now().plusDays(2));
        when(screeningInvitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(invitation));
        return invitation;
    }

    // `index` é 0-based (0 = "Discordo totalmente", 4 = "Concordo totalmente").
    private ScreeningSubmissionRequestDTO answers(Object... questionIdThenIndex) {
        List<ScreeningAnswerSubmitDTO> list = new ArrayList<>();
        for (int i = 0; i < questionIdThenIndex.length; i += 2) {
            list.add(new ScreeningAnswerSubmitDTO(
                    (Long) questionIdThenIndex[i], (Integer) questionIdThenIndex[i + 1], null, 5));
        }
        return new ScreeningSubmissionRequestDTO(list, 120, 4);
    }

    private double scoreOf(ScreeningTraitProfileDTO profile, BigFiveDimension dimension) {
        return profile.scores().stream()
                .filter(s -> s.dimension() == dimension)
                .map(ScreeningTraitScoreDTO::score)
                .findFirst()
                .orElseThrow();
    }

    // ── pontuação ──────────────────────────────────────────────────────

    @Test
    void submitScoresEachDimensionIndependently() {
        ScreeningStage stage = behavioralStage(1L);
        likertItem(stage, 11L, BigFiveDimension.EXTRAVERSION, false);
        likertItem(stage, 12L, BigFiveDimension.EXTRAVERSION, false);
        likertItem(stage, 13L, BigFiveDimension.OPENNESS, false);

        ScreeningInvitation invitation = invitationFor(stage);

        // Extroversão no topo da escala (4 = "Concordo totalmente"), Abertura no piso (0).
        ScreeningInvitationService.SubmitResult result = invitationService.submit(
                INVITATION_ID, PROFESSIONAL_ID, answers(11L, 4, 12L, 4, 13L, 0));

        ScreeningTraitProfileDTO profile =
                ScreeningTraitProfileDTO.from(result.invitation().getTraitScores());
        assertNotNull(profile);
        assertEquals(2, profile.scores().size(), "só dimensões com item respondido entram no perfil");
        assertEquals(100.0, scoreOf(profile, BigFiveDimension.EXTRAVERSION));
        assertEquals(0.0, scoreOf(profile, BigFiveDimension.OPENNESS));
    }

    @Test
    void reverseScoredItemIsInverted() {
        ScreeningStage stage = behavioralStage(1L);
        // Dois itens da mesma dimensão, um direto e um reverso, respondidos com o mesmo valor
        // extremo: um puxa pra cima, o outro pra baixo, e o resultado tem que cair no meio.
        likertItem(stage, 21L, BigFiveDimension.CONSCIENTIOUSNESS, false);
        likertItem(stage, 22L, BigFiveDimension.CONSCIENTIOUSNESS, true);
        invitationFor(stage);

        ScreeningInvitationService.SubmitResult result = invitationService.submit(
                INVITATION_ID, PROFESSIONAL_ID, answers(21L, 4, 22L, 4));

        ScreeningTraitProfileDTO profile =
                ScreeningTraitProfileDTO.from(result.invitation().getTraitScores());
        assertEquals(50.0, scoreOf(profile, BigFiveDimension.CONSCIENTIOUSNESS));
    }

    @Test
    void reverseScoredItemAloneFlipsTheScale() {
        ScreeningStage stage = behavioralStage(1L);
        likertItem(stage, 31L, BigFiveDimension.NEUROTICISM, true);
        invitationFor(stage);

        // Concordou totalmente com um item REVERSO -> pontua no piso da dimensão, não no topo.
        ScreeningInvitationService.SubmitResult result =
                invitationService.submit(INVITATION_ID, PROFESSIONAL_ID, answers(31L, 4));

        ScreeningTraitProfileDTO profile =
                ScreeningTraitProfileDTO.from(result.invitation().getTraitScores());
        assertEquals(0.0, scoreOf(profile, BigFiveDimension.NEUROTICISM));
        assertEquals(1, profile.scores().get(0).answeredItemCount());
    }

    @Test
    void behavioralSubmitLeavesAutoScoreNull() {
        ScreeningStage stage = behavioralStage(1L);
        likertItem(stage, 41L, BigFiveDimension.AGREEABLENESS, false);
        invitationFor(stage);

        ScreeningInvitationService.SubmitResult result =
                invitationService.submit(INVITATION_ID, PROFESSIONAL_ID, answers(41L, 2));

        assertNull(result.invitation().getAutoScorePercent(),
                "não há gabarito numa etapa comportamental -- a nota tem que ficar nula");
    }

    @Test
    void likertAnswerOutOfRangeIsRejected() {
        ScreeningStage stage = behavioralStage(1L);
        likertItem(stage, 51L, BigFiveDimension.OPENNESS, false);
        invitationFor(stage);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> invitationService.submit(INVITATION_ID, PROFESSIONAL_ID, answers(51L, 5)));
        assertEquals(400, error.getStatusCode().value());
    }

    // ── REGRESSAO: etapa QUESTIONS nao mudou ───────────────────────────

    private ScreeningStage questionsStage(Long id) {
        ScreeningStage stage = new ScreeningStage();
        stage.setId(id);
        stage.setKind(ScreeningStageKind.QUESTIONS);
        stage.setTitle("Triagem tecnica");
        stage.setOrderIndex(questionnaire.getStages().size());
        stage.setResponseDeadlineDays(3);
        stage.setActive(true);
        stage.setScreeningQuestionnaire(questionnaire);
        questionnaire.getStages().add(stage);
        return stage;
    }

    private ScreeningQuestion multipleChoice(ScreeningStage stage, Long id, int correctIndex) {
        ScreeningQuestion question = new ScreeningQuestion();
        question.setId(id);
        question.setScreeningStage(stage);
        question.setType(ScreeningQuestionType.MULTIPLE_CHOICE);
        question.setPrompt("Questao " + id);
        question.setOptions(new ArrayList<>(List.of("A", "B", "C")));
        question.setCorrectOptionIndex(correctIndex);
        question.setOrderIndex(stage.getQuestions().size());
        question.setActive(true);
        stage.getQuestions().add(question);
        return question;
    }

    // O caminho de submissao foi reescrito nos Prompts 1 e 2 (mapa de respostas pre-existentes,
    // ramo Likert, ramo de video, auto-aprovacao comportamental). Uma vaga que so usa os tipos
    // antigos NAO pode ter sentido nada disso -- e nada cobria isso ate aqui.
    @Test
    void ordinaryStageSubmitIsUnchanged() {
        ScreeningStage stage = questionsStage(1L);
        multipleChoice(stage, 91L, 1);
        multipleChoice(stage, 92L, 2);

        ScreeningQuestion essay = new ScreeningQuestion();
        essay.setId(93L);
        essay.setScreeningStage(stage);
        essay.setType(ScreeningQuestionType.ESSAY);
        essay.setPrompt("Descreva um desafio");
        essay.setOrderIndex(2);
        essay.setActive(true);
        stage.getQuestions().add(essay);

        ScreeningInvitation invitation = invitationFor(stage);

        List<ScreeningAnswerSubmitDTO> submitted = List.of(
                new ScreeningAnswerSubmitDTO(91L, 1, null, 10),   // acerto
                new ScreeningAnswerSubmitDTO(92L, 0, null, 10),   // erro
                new ScreeningAnswerSubmitDTO(93L, null, "minha resposta", 30));

        ScreeningInvitationService.SubmitResult result = invitationService.submit(
                INVITATION_ID, PROFESSIONAL_ID,
                new ScreeningSubmissionRequestDTO(submitted, 120, 4));

        // Continua esperando decisao manual da empresa -- nada de auto-aprovar nem auto-avancar.
        assertEquals(ScreeningInvitationStatus.SUBMITTED, result.invitation().getStatus());
        assertNull(result.invitation().getDecidedAt());
        assertFalse(result.autoAdvanced());

        // Nota so das multiplas escolhas: 1 de 2 = 50%.
        assertEquals(50.0, result.invitation().getAutoScorePercent());
        assertTrue(result.invitation().getTraitScores().isEmpty(),
                "etapa de perguntas nao produz perfil de tracos");

        assertEquals(3, invitation.getAnswers().size());
        assertTrue(invitation.getAnswers().get(0).getCorrect());
        assertFalse(invitation.getAnswers().get(1).getCorrect());
        assertNull(invitation.getAnswers().get(2).getCorrect(), "dissertativa nao tem gabarito");
        assertEquals("minha resposta", invitation.getAnswers().get(2).getEssayText());

        // A empresa continua vendo tudo: respostas item a item e a telemetria de troca de aba.
        ScreeningInvitationDetailDTO forCompany =
                invitationService.toDetailDTO(result.invitation(), true);
        assertEquals(3, forCompany.answers().size());
        assertEquals(4, forCompany.tabSwitchCount());
        assertNull(forCompany.traitProfile());
        assertEquals(ScreeningStageKind.QUESTIONS, forCompany.stageKind());
    }

    @Test
    void ordinaryStageStillRequiresEveryAnswer() {
        ScreeningStage stage = questionsStage(1L);
        multipleChoice(stage, 94L, 0);
        multipleChoice(stage, 95L, 0);
        invitationFor(stage);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> invitationService.submit(INVITATION_ID, PROFESSIONAL_ID,
                        new ScreeningSubmissionRequestDTO(
                                List.of(new ScreeningAnswerSubmitDTO(94L, 0, null, 5)), 60, 0)));

        assertEquals(400, error.getStatusCode().value());
        assertTrue(error.getReason().contains("Missing answer"));
    }

    @Test
    void ordinaryStageIsStillDecidedByTheCompany() {
        ScreeningStage stage = questionsStage(1L);
        ScreeningInvitation invitation = invitationFor(stage);
        invitation.setStatus(ScreeningInvitationStatus.SUBMITTED);

        ScreeningInvitationService.StageDecision decision =
                invitationService.approveStage(INVITATION_ID, COMPANY_ID, "ok");

        assertEquals(ScreeningInvitationStatus.APPROVED, decision.invitation().getStatus());
        assertTrue(decision.wasLastStage());
        assertEquals("ok", decision.invitation().getCompanyDecisionComment());
    }

    // ── a etapa nunca reprova ──────────────────────────────────────────

    @Test
    void behavioralStageApprovesItselfOnSubmit() {
        ScreeningStage stage = behavioralStage(1L);
        likertItem(stage, 61L, BigFiveDimension.EXTRAVERSION, false);
        invitationFor(stage);

        ScreeningInvitationService.SubmitResult result =
                invitationService.submit(INVITATION_ID, PROFESSIONAL_ID, answers(61L, 3));

        assertEquals(ScreeningInvitationStatus.APPROVED, result.invitation().getStatus(),
                "etapa informativa não fica esperando decisão da empresa");
        assertNotNull(result.invitation().getDecidedAt());
        assertTrue(result.autoAdvanced());
        assertTrue(result.wasLastStage(), "era a única etapa do processo");

        // A empresa é avisada, mas com o texto informativo -- nunca com o pedido de decisão.
        verify(notificationService, never())
                .notifyScreeningSubmitted(any(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void behavioralStageOpensTheNextStage() {
        ScreeningStage behavioral = behavioralStage(1L);
        likertItem(behavioral, 71L, BigFiveDimension.OPENNESS, false);

        ScreeningStage next = new ScreeningStage();
        next.setId(2L);
        next.setKind(ScreeningStageKind.QUESTIONS);
        next.setTitle("Técnica");
        next.setOrderIndex(1);
        next.setResponseDeadlineDays(5);
        next.setActive(true);
        next.setScreeningQuestionnaire(questionnaire);
        questionnaire.getStages().add(next);

        invitationFor(behavioral);

        ScreeningInvitationService.SubmitResult result =
                invitationService.submit(INVITATION_ID, PROFESSIONAL_ID, answers(71L, 1));

        assertTrue(result.autoAdvanced());
        assertFalse(result.wasLastStage(), "havia uma etapa seguinte, então nada é retomado ainda");
        verify(notificationService)
                .notifyScreeningStageApproved(any(), anyString(), anyString(), any());
    }

    @Test
    void companyCannotApproveOrReproveBehavioralStage() {
        ScreeningStage stage = behavioralStage(1L);
        ScreeningInvitation invitation = invitationFor(stage);
        invitation.setStatus(ScreeningInvitationStatus.SUBMITTED);

        ResponseStatusException onApprove = assertThrows(ResponseStatusException.class,
                () -> invitationService.approveStage(INVITATION_ID, COMPANY_ID, "ok"));
        ResponseStatusException onReprove = assertThrows(ResponseStatusException.class,
                () -> invitationService.reproveStage(INVITATION_ID, COMPANY_ID, "não"));

        assertEquals(400, onApprove.getStatusCode().value());
        assertEquals(400, onReprove.getStatusCode().value());
        assertTrue(onReprove.getReason().contains("informational"));
        assertEquals(ScreeningInvitationStatus.SUBMITTED, invitation.getStatus(),
                "nem aprovar nem reprovar pode ter mexido no estado");
    }

    @Test
    void behavioralStageIsNeverDecidableEvenIfItSomehowReachesSubmitted() {
        ScreeningStage stage = behavioralStage(1L);
        ScreeningInvitation invitation = invitationFor(stage);

        // Percorre TODO status: em nenhum deles a etapa comportamental aceita decisao da empresa.
        // A UI ja nao oferece o botao (needsDecision exige SUBMITTED, que uma etapa comportamental
        // nunca alcanca), mas a regra nao pode depender disso -- assertDecidable barra antes de
        // qualquer checagem de estado.
        for (ScreeningInvitationStatus status : ScreeningInvitationStatus.values()) {
            invitation.setStatus(status);

            ResponseStatusException onReprove = assertThrows(ResponseStatusException.class,
                    () -> invitationService.reproveStage(INVITATION_ID, COMPANY_ID, "nao"),
                    "reprovar deveria falhar no status " + status);
            assertEquals(400, onReprove.getStatusCode().value());
            assertTrue(onReprove.getReason().contains("informational"));

            assertThrows(ResponseStatusException.class,
                    () -> invitationService.approveStage(INVITATION_ID, COMPANY_ID, "ok"),
                    "aprovar deveria falhar no status " + status);

            assertEquals(status, invitation.getStatus(),
                    "nenhuma das duas tentativas pode ter mexido no estado");
        }
    }

    // ── o que a empresa vê ─────────────────────────────────────────────

    @Test
    void companyViewHidesTabSwitchAndItemAnswersButKeepsProfile() {
        ScreeningStage stage = behavioralStage(1L);
        likertItem(stage, 81L, BigFiveDimension.EXTRAVERSION, false);
        invitationFor(stage);

        ScreeningInvitationService.SubmitResult result =
                invitationService.submit(INVITATION_ID, PROFESSIONAL_ID, answers(81L, 4));
        ScreeningInvitation submitted = result.invitation();

        assertEquals(4, submitted.getTabSwitchCount(),
                "continua GRAVADO no banco -- o que muda é só o que sai no DTO");

        ScreeningInvitationDetailDTO forCompany = invitationService.toDetailDTO(submitted, true);
        assertNull(forCompany.tabSwitchCount(), "sinal sem significado num teste sem resposta certa");
        assertTrue(forCompany.answers().isEmpty(), "empresa recebe o agregado, não o item a item");
        assertNotNull(forCompany.traitProfile());
        assertEquals(ScreeningTraitProfileDTO.DISCLAIMER, forCompany.traitProfile().disclaimer());

        ScreeningInvitationDetailDTO forProfessional = invitationService.toDetailDTO(submitted, false);
        assertEquals(1, forProfessional.answers().size(), "o candidato vê as próprias respostas");
        assertNotNull(forProfessional.traitProfile());
    }

    // ── montagem da etapa (ScreeningQuestionnaireService) ───────────────

    private ScreeningQuestionnaireRequestDTO requestWithBehavioralStage(
            List<ScreeningQuestionRequestDTO> questions) {
        return new ScreeningQuestionnaireRequestDTO(
                PROJECT_ID, "Processo Teste", null,
                List.of(new ScreeningStageRequestDTO(
                        null, ScreeningStageKind.BEHAVIORAL, null, "Perfil comportamental", null, 3, questions)));
    }

    private BehavioralItem bankItem(Long id, BigFiveDimension dimension, boolean reverse, int order) {
        BehavioralItem item = new BehavioralItem();
        item.setId(id);
        item.setDimension(dimension);
        item.setPrompt("Item de banco " + id);
        item.setReverseScored(reverse);
        item.setOrderIndex(order);
        item.setActive(true);
        return item;
    }

    @Test
    void behavioralStageIsBuiltFromThePlatformItemBank() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(screeningQuestionnaireRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
        when(screeningQuestionnaireRepository.save(any(ScreeningQuestionnaire.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(behavioralItemRepository.findByActiveTrueOrderByOrderIndexAsc()).thenReturn(List.of(
                bankItem(1L, BigFiveDimension.EXTRAVERSION, false, 0),
                bankItem(2L, BigFiveDimension.NEUROTICISM, true, 1)));

        ScreeningQuestionnaire created =
                questionnaireService.create(requestWithBehavioralStage(List.of()), COMPANY_ID);

        ScreeningStage stage = created.getStages().get(0);
        assertEquals(ScreeningStageKind.BEHAVIORAL, stage.getKind());
        assertEquals(2, stage.getQuestions().size());

        ScreeningQuestion first = stage.getQuestions().get(0);
        assertEquals(ScreeningQuestionType.LIKERT_SCALE, first.getType());
        assertEquals(BigFiveDimension.EXTRAVERSION, first.getTraitDimension());
        assertNull(first.getCorrectOptionIndex(), "inventário de personalidade não tem gabarito");
        assertTrue(first.getOptions().isEmpty(), "a escala Likert é fixa, não vive em options");
        assertTrue(stage.getQuestions().get(1).getReverseScored());
    }

    @Test
    void behavioralStageRejectsCompanyWrittenQuestions() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(screeningQuestionnaireRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());

        ScreeningQuestionnaireRequestDTO request = requestWithBehavioralStage(List.of(
                new ScreeningQuestionRequestDTO(
                        null, ScreeningQuestionType.MULTIPLE_CHOICE, "Você é proativo?",
                        List.of("Sim", "Não"), 0)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> questionnaireService.create(request, COMPANY_ID));
        assertEquals(400, error.getStatusCode().value());
        verify(behavioralItemRepository, never()).findByActiveTrueOrderByOrderIndexAsc();
    }

    @Test
    void likertItemCannotBeCreatedInsideAnOrdinaryStage() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(screeningQuestionnaireRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());

        ScreeningQuestionnaireRequestDTO request = new ScreeningQuestionnaireRequestDTO(
                PROJECT_ID, "Processo Teste", null,
                List.of(new ScreeningStageRequestDTO(
                        null, ScreeningStageKind.QUESTIONS, null, "Técnica", null, 3,
                        List.of(new ScreeningQuestionRequestDTO(
                                null, ScreeningQuestionType.LIKERT_SCALE, "Sou a alma da festa.",
                                List.of(), null)))));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> questionnaireService.create(request, COMPANY_ID));
        assertEquals(400, error.getStatusCode().value());
    }
}
