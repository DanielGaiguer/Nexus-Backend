package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.main.nexus.dto.ApplyAssessmentTemplateRequestDTO;
import com.main.nexus.dto.AssessmentTemplateQuestionRequestDTO;
import com.main.nexus.dto.AssessmentTemplateRequestDTO;
import com.main.nexus.model.AssessmentTemplate;
import com.main.nexus.model.Company;
import com.main.nexus.model.Project;
import com.main.nexus.model.ScreeningQuestion;
import com.main.nexus.model.ScreeningQuestionnaire;
import com.main.nexus.model.ScreeningStage;
import com.main.nexus.model.enums.CompanyMemberRole;
import com.main.nexus.model.enums.ScreeningQuestionType;
import com.main.nexus.model.enums.ScreeningStageKind;
import com.main.nexus.repository.AssessmentTemplateRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.ScreeningQuestionnaireRepository;
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

// Biblioteca de testes reutilizáveis (Prompt 3/7). O teste que mais importa aqui é
// `editingTheTemplateDoesNotTouchStagesAlreadyApplied`: é ele que prova que a aplicação é CÓPIA
// e não referência -- se um dia alguém trocar isso por um vínculo, é este que quebra.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssessmentTemplateServiceTest {

    private static final Long COMPANY_ID = 10L;
    private static final Long OTHER_COMPANY_ID = 11L;
    private static final Long PROJECT_ID = 100L;
    private static final Long TEMPLATE_ID = 300L;

    @Mock private AssessmentTemplateRepository assessmentTemplateRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ScreeningQuestionnaireRepository screeningQuestionnaireRepository;
    @Mock private CompanyAccessService companyAccessService;

    @InjectMocks private AssessmentTemplateService templateService;

    private Company company;
    private Project project;
    private CompanyAccessService.CompanyAccess access;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(COMPANY_ID);
        company.setCompanyName("Empresa Teste");

        project = new Project();
        project.setId(PROJECT_ID);
        project.setTitle("Dev Backend");
        project.setCompany(company);

        access = new CompanyAccessService.CompanyAccess(company, CompanyMemberRole.MEMBER);

        when(assessmentTemplateRepository.save(any(AssessmentTemplate.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(screeningQuestionnaireRepository.save(any(ScreeningQuestionnaire.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
    }

    // ── helpers ────────────────────────────────────────────────────────

    private AssessmentTemplateQuestionRequestDTO multipleChoice(String prompt, int correct) {
        return new AssessmentTemplateQuestionRequestDTO(
                ScreeningQuestionType.MULTIPLE_CHOICE, prompt, List.of("A", "B", "C"), correct);
    }

    private AssessmentTemplateQuestionRequestDTO essay(String prompt) {
        return new AssessmentTemplateQuestionRequestDTO(
                ScreeningQuestionType.ESSAY, prompt, null, null);
    }

    private AssessmentTemplateRequestDTO request(
            String title, List<AssessmentTemplateQuestionRequestDTO> questions) {
        return new AssessmentTemplateRequestDTO(title, null, "Sem consulta.", 5, questions);
    }

    private AssessmentTemplate createDefaultTemplate() {
        AssessmentTemplate template = templateService.create(
                request("Lógica básica", List.of(multipleChoice("2 + 2?", 1), essay("Explique."))),
                access);
        template.setId(TEMPLATE_ID);
        when(assessmentTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        return template;
    }

    private ScreeningQuestionnaire existingQuestionnaire() {
        ScreeningQuestionnaire questionnaire = new ScreeningQuestionnaire();
        questionnaire.setId(1L);
        questionnaire.setProject(project);
        questionnaire.setTitle("Processo existente");
        when(screeningQuestionnaireRepository.findByProjectId(PROJECT_ID))
                .thenReturn(Optional.of(questionnaire));
        return questionnaire;
    }

    // ── CRUD ───────────────────────────────────────────────────────────

    @Test
    void createBuildsTheTemplateWithOrderedQuestions() {
        AssessmentTemplate template = templateService.create(
                request("Lógica básica", List.of(multipleChoice("2 + 2?", 1), essay("Explique."))),
                access);

        assertEquals(COMPANY_ID, template.getCompany().getId());
        assertEquals(ScreeningStageKind.QUESTIONS, template.getKind());
        assertEquals(5, template.getResponseDeadlineDays());
        assertTrue(template.getActive());
        assertEquals(2, template.getQuestions().size());
        assertEquals(0, template.getQuestions().get(0).getOrderIndex());
        assertEquals(1, template.getQuestions().get(1).getOrderIndex());
        assertEquals(1, template.getQuestions().get(0).getCorrectOptionIndex());
        assertNull(template.getQuestions().get(1).getCorrectOptionIndex(),
                "dissertativa não tem gabarito");
    }

    @Test
    void anyActiveMemberCanManageTemplates() {
        templateService.create(request("Lógica", List.of(essay("Explique."))), access);

        // Conteúdo de recrutamento: guard de MEMBRO, nunca de dono -- requireOwner fica pro que é
        // da conta (cobrança, membros, exclusão).
        verify(companyAccessService).requireMember(CompanyMemberRole.MEMBER);
    }

    @Test
    void updateReplacesTheQuestionListWholesale() {
        AssessmentTemplate template = createDefaultTemplate();

        templateService.update(TEMPLATE_ID,
                request("Lógica v2", List.of(multipleChoice("3 + 3?", 2))), access);

        assertEquals("Lógica v2", template.getTitle());
        assertEquals(1, template.getQuestions().size());
        assertEquals("3 + 3?", template.getQuestions().get(0).getPrompt());
    }

    @Test
    void retiringATemplateKeepsTheRow() {
        AssessmentTemplate template = createDefaultTemplate();

        templateService.setActive(TEMPLATE_ID, false, access);

        assertFalse(template.getActive());
        // Aposentar não apaga: a linha continua acessível, porque etapas já geradas apontam pra
        // ela em ScreeningStage.sourceTemplateId.
        assertEquals(template, templateService.getForCompany(TEMPLATE_ID, COMPANY_ID));
    }

    @Test
    void aRetiredTemplateCannotBeApplied() {
        AssessmentTemplate template = createDefaultTemplate();
        template.setActive(false);
        existingQuestionnaire();

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> templateService.applyToProject(TEMPLATE_ID,
                        new ApplyAssessmentTemplateRequestDTO(PROJECT_ID, null, null), access));

        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void onlyQuestionBasedTemplatesAreAccepted() {
        AssessmentTemplateRequestDTO behavioral = new AssessmentTemplateRequestDTO(
                "Perfil", ScreeningStageKind.BEHAVIORAL, null, 3, List.of(essay("x")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> templateService.create(behavioral, access));

        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void likertAndVideoQuestionsAreRejectedInsideATemplate() {
        AssessmentTemplateRequestDTO withVideo = request("Teste", List.of(
                new AssessmentTemplateQuestionRequestDTO(
                        ScreeningQuestionType.VIDEO_RESPONSE, "Fale sobre você", null, null)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> templateService.create(withVideo, access));

        assertEquals(400, error.getStatusCode().value());
    }

    // ── aplicar a uma vaga ─────────────────────────────────────────────

    @Test
    void applyingCopiesTheQuestionsIntoANewStage() {
        createDefaultTemplate();
        ScreeningQuestionnaire questionnaire = existingQuestionnaire();

        ScreeningStage stage = templateService.applyToProject(TEMPLATE_ID,
                new ApplyAssessmentTemplateRequestDTO(PROJECT_ID, null, null), access);

        assertEquals(ScreeningStageKind.QUESTIONS, stage.getKind());
        assertEquals("Lógica básica", stage.getTitle());
        assertEquals(5, stage.getResponseDeadlineDays(), "prazo padrão vem do molde");
        assertEquals(TEMPLATE_ID, stage.getSourceTemplateId(),
                "rastro pra agrupar resultados entre vagas depois");
        assertTrue(questionnaire.getStages().contains(stage));

        assertEquals(2, stage.getQuestions().size());
        ScreeningQuestion first = stage.getQuestions().get(0);
        assertEquals(ScreeningQuestionType.MULTIPLE_CHOICE, first.getType());
        assertEquals("2 + 2?", first.getPrompt());
        assertEquals(List.of("A", "B", "C"), first.getOptions());
        assertEquals(1, first.getCorrectOptionIndex());
        assertTrue(first.getActive());
        assertEquals(ScreeningQuestionType.ESSAY, stage.getQuestions().get(1).getType());
    }

    @Test
    void applyingHonoursPerOpportunityOverrides() {
        createDefaultTemplate();
        existingQuestionnaire();

        ScreeningStage stage = templateService.applyToProject(TEMPLATE_ID,
                new ApplyAssessmentTemplateRequestDTO(PROJECT_ID, "Prova de lógica — Backend", 10),
                access);

        assertEquals("Prova de lógica — Backend", stage.getTitle());
        assertEquals(10, stage.getResponseDeadlineDays());
    }

    @Test
    void appliedStageGoesToTheEndOfTheProcess() {
        createDefaultTemplate();
        ScreeningQuestionnaire questionnaire = existingQuestionnaire();

        ScreeningStage existing = new ScreeningStage();
        existing.setId(9L);
        existing.setOrderIndex(4);
        existing.setTitle("Etapa anterior");
        existing.setActive(false);
        questionnaire.getStages().add(existing);

        ScreeningStage stage = templateService.applyToProject(TEMPLATE_ID,
                new ApplyAssessmentTemplateRequestDTO(PROJECT_ID, null, null), access);

        assertEquals(5, stage.getOrderIndex(),
                "max+1, não size() -- etapa desativada mantém o orderIndex que tinha");
    }

    @Test
    void applyingToAnOpportunityWithoutAScreeningProcessCreatesOne() {
        createDefaultTemplate();
        when(screeningQuestionnaireRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());

        ScreeningStage stage = templateService.applyToProject(TEMPLATE_ID,
                new ApplyAssessmentTemplateRequestDTO(PROJECT_ID, null, null), access);

        ScreeningQuestionnaire created = stage.getScreeningQuestionnaire();
        assertEquals(PROJECT_ID, created.getProject().getId());
        assertEquals("Processo seletivo — Dev Backend", created.getTitle());
        assertEquals(1, created.getStages().size());
    }

    // ── A PROVA DE QUE É CÓPIA ─────────────────────────────────────────

    @Test
    void editingTheTemplateDoesNotTouchStagesAlreadyApplied() {
        createDefaultTemplate();
        existingQuestionnaire();

        ScreeningStage stage = templateService.applyToProject(TEMPLATE_ID,
                new ApplyAssessmentTemplateRequestDTO(PROJECT_ID, null, null), access);

        // Reescreve o molde inteiro: outro título, outra pergunta, outro gabarito.
        templateService.update(TEMPLATE_ID,
                request("Outro teste", List.of(multipleChoice("Pergunta nova", 2))), access);

        assertEquals("Lógica básica", stage.getTitle(), "a etapa não acompanha o molde");
        assertEquals(2, stage.getQuestions().size());
        assertEquals("2 + 2?", stage.getQuestions().get(0).getPrompt());
        assertEquals(1, stage.getQuestions().get(0).getCorrectOptionIndex());
    }

    @Test
    void appliedOptionsAreADefensiveCopy() {
        AssessmentTemplate template = createDefaultTemplate();
        existingQuestionnaire();

        ScreeningStage stage = templateService.applyToProject(TEMPLATE_ID,
                new ApplyAssessmentTemplateRequestDTO(PROJECT_ID, null, null), access);

        // Sem a cópia defensiva as duas entidades compartilhariam a mesma List, e mexer nas
        // alternativas da etapa editaria o molde por tabela.
        assertNotSame(template.getQuestions().get(0).getOptions(),
                stage.getQuestions().get(0).getOptions());
    }

    // O caso real de uso da biblioteca: o MESMO molde em mais de uma vaga. Cada aplicacao tem
    // que produzir uma etapa independente -- e editar o molde depois nao pode alcancar nenhuma
    // das duas.
    @Test
    void theSameTemplateCanBeAppliedToTwoDifferentOpportunities() {
        createDefaultTemplate();
        ScreeningQuestionnaire firstQuestionnaire = existingQuestionnaire();

        ScreeningStage firstStage = templateService.applyToProject(TEMPLATE_ID,
                new ApplyAssessmentTemplateRequestDTO(PROJECT_ID, null, null), access);

        // Segunda vaga, da mesma empresa, com processo seletivo proprio.
        Long secondProjectId = 101L;
        Project secondProject = new Project();
        secondProject.setId(secondProjectId);
        secondProject.setTitle("Dev Frontend");
        secondProject.setCompany(company);
        when(projectRepository.findById(secondProjectId)).thenReturn(Optional.of(secondProject));

        ScreeningQuestionnaire secondQuestionnaire = new ScreeningQuestionnaire();
        secondQuestionnaire.setId(2L);
        secondQuestionnaire.setProject(secondProject);
        secondQuestionnaire.setTitle("Processo da segunda vaga");
        when(screeningQuestionnaireRepository.findByProjectId(secondProjectId))
                .thenReturn(Optional.of(secondQuestionnaire));

        ScreeningStage secondStage = templateService.applyToProject(TEMPLATE_ID,
                new ApplyAssessmentTemplateRequestDTO(secondProjectId, null, null), access);

        assertNotSame(firstStage, secondStage);
        assertTrue(firstQuestionnaire.getStages().contains(firstStage));
        assertTrue(secondQuestionnaire.getStages().contains(secondStage));
        assertEquals(1, firstQuestionnaire.getStages().size(),
                "aplicar na segunda vaga nao pode mexer na primeira");

        // As duas etapas nao compartilham NENHUMA instancia -- nem as questoes nem as listas de
        // alternativas. Se compartilhassem, editar a prova de uma vaga editaria a da outra.
        assertNotSame(firstStage.getQuestions().get(0), secondStage.getQuestions().get(0));
        assertNotSame(firstStage.getQuestions().get(0).getOptions(),
                secondStage.getQuestions().get(0).getOptions());
        assertEquals("2 + 2?", secondStage.getQuestions().get(0).getPrompt());

        // Editar o molde depois nao alcanca nenhuma das duas.
        templateService.update(TEMPLATE_ID,
                request("Outro teste", List.of(multipleChoice("Pergunta nova", 2))), access);

        assertEquals("2 + 2?", firstStage.getQuestions().get(0).getPrompt());
        assertEquals("2 + 2?", secondStage.getQuestions().get(0).getPrompt());
        assertEquals(2, firstStage.getQuestions().size());
        assertEquals(2, secondStage.getQuestions().size());
    }

    // ── guards ─────────────────────────────────────────────────────────

    @Test
    void aTemplateFromAnotherCompanyIsNotVisible() {
        AssessmentTemplate foreign = new AssessmentTemplate();
        foreign.setId(TEMPLATE_ID);
        Company other = new Company();
        other.setId(OTHER_COMPANY_ID);
        foreign.setCompany(other);
        when(assessmentTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(foreign));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> templateService.getForCompany(TEMPLATE_ID, COMPANY_ID));

        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void aTemplateCannotBeAppliedToAnotherCompanysOpportunity() {
        createDefaultTemplate();
        Company other = new Company();
        other.setId(OTHER_COMPANY_ID);
        project.setCompany(other);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> templateService.applyToProject(TEMPLATE_ID,
                        new ApplyAssessmentTemplateRequestDTO(PROJECT_ID, null, null), access));

        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void applyingRequiresAProjectId() {
        createDefaultTemplate();

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> templateService.applyToProject(TEMPLATE_ID,
                        new ApplyAssessmentTemplateRequestDTO(null, null, null), access));

        assertEquals(400, error.getStatusCode().value());
    }
}
