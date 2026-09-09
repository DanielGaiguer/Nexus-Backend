package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.main.nexus.model.Company;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.ScreeningAnswer;
import com.main.nexus.model.ScreeningInvitation;
import com.main.nexus.model.ScreeningQuestion;
import com.main.nexus.model.ScreeningQuestionnaire;
import com.main.nexus.model.ScreeningStage;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.CompanyType;
import com.main.nexus.model.enums.ScreeningInvitationStatus;
import com.main.nexus.model.enums.ScreeningQuestionType;
import com.main.nexus.model.enums.ScreeningStageKind;
import com.main.nexus.model.enums.UserType;
import com.main.nexus.repository.CommissionChargeRepository;
import com.main.nexus.repository.CompanyBillingProfileRepository;
import com.main.nexus.repository.CompanyFiscalProfileRepository;
import com.main.nexus.repository.CustomPortalRepository;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.MessageRepository;
import com.main.nexus.repository.NfseInvoiceRepository;
import com.main.nexus.repository.NotificationRepository;
import com.main.nexus.repository.PortalSubscriptionChargeRepository;
import com.main.nexus.repository.PreviousProjectRepository;
import com.main.nexus.repository.ProfessionalCredentialRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.ProposalRepository;
import com.main.nexus.repository.ReviewRepository;
import com.main.nexus.repository.ScreeningInvitationRepository;
import com.main.nexus.repository.SupportConversationRepository;
import com.main.nexus.repository.SupportMessageRepository;
import com.main.nexus.repository.UserConsentRepository;
import com.main.nexus.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// Regra 2 (minimização de dado de terceiro) aplicada ao campo novo: o vídeo do candidato entra no
// export do PRÓPRIO candidato e não no export da empresa. Rosto e voz não são dado que a empresa
// porta consigo -- mesma régua que já mantinha "myAnswers" fora do export do contratante.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserDataExportVideoTest {

    private static final Long COMPANY_USER_ID = 1L;
    private static final Long PROFESSIONAL_USER_ID = 2L;
    private static final Long COMPANY_ID = 10L;
    private static final Long PROFESSIONAL_ID = 55L;
    private static final String OBJECT_URL =
            "https://sb.test/storage/v1/object/screening-videos-nexus/screenings/900/77/abc.webm";
    private static final String SIGNED_URL = "https://sb.test/signed?token=export";

    @Mock private UserRepository userRepository;
    @Mock private ProfessionalService professionalService;
    @Mock private CompanyService companyService;
    @Mock private PreviousProjectRepository previousProjectRepository;
    @Mock private ProfessionalCredentialRepository credentialRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private ProposalRepository proposalRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserConsentRepository consentRepository;
    @Mock private ScreeningInvitationRepository screeningInvitationRepository;
    @Mock private ScreeningVideoService screeningVideoService;
    @Mock private SupportConversationRepository supportConversationRepository;
    @Mock private SupportMessageRepository supportMessageRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private CommissionChargeRepository commissionChargeRepository;
    @Mock private NfseInvoiceRepository nfseInvoiceRepository;
    @Mock private PortalSubscriptionChargeRepository portalSubscriptionChargeRepository;
    @Mock private CompanyBillingProfileRepository billingProfileRepository;
    @Mock private CompanyFiscalProfileRepository fiscalProfileRepository;
    @Mock private CustomPortalRepository customPortalRepository;
    @Mock private EmailService emailService;

    @InjectMocks private UserDataExportService exportService;

    private ScreeningInvitation invitation;

    @BeforeEach
    void setUp() {
        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setCompanyName("Empresa Teste");
        company.setType(CompanyType.LEGAL_ENTITY);

        Project project = new Project();
        project.setId(100L);
        project.setTitle("Vaga Teste");
        project.setCompany(company);

        ScreeningQuestionnaire questionnaire = new ScreeningQuestionnaire();
        questionnaire.setId(1L);
        questionnaire.setProject(project);

        ScreeningStage stage = new ScreeningStage();
        stage.setId(5L);
        stage.setKind(ScreeningStageKind.VIDEO);
        stage.setTitle("Vídeo");
        stage.setResponseDeadlineDays(3);
        stage.setActive(true);
        stage.setScreeningQuestionnaire(questionnaire);

        ScreeningQuestion question = new ScreeningQuestion();
        question.setId(77L);
        question.setScreeningStage(stage);
        question.setType(ScreeningQuestionType.VIDEO_RESPONSE);
        question.setPrompt("Fale sobre você");
        question.setActive(true);
        stage.getQuestions().add(question);

        User professionalUser = new User();
        professionalUser.setId(PROFESSIONAL_USER_ID);
        professionalUser.setEmail("pro@teste.com");
        professionalUser.setType(UserType.PROFESSIONAL);

        Professional professional = new Professional();
        professional.setId(PROFESSIONAL_ID);
        professional.setName("Candidato Teste");
        professional.setUser(professionalUser);

        invitation = new ScreeningInvitation();
        invitation.setId(900L);
        invitation.setScreeningStage(stage);
        invitation.setProfessional(professional);
        invitation.setStatus(ScreeningInvitationStatus.SUBMITTED);
        invitation.setSentAt(LocalDateTime.now().minusDays(1));
        invitation.setDeadlineAt(LocalDateTime.now().plusDays(2));

        ScreeningAnswer answer = new ScreeningAnswer();
        answer.setScreeningInvitation(invitation);
        answer.setScreeningQuestion(question);
        answer.setVideoUrl(OBJECT_URL);
        answer.setVideoDurationSeconds(42);
        invitation.getAnswers().add(answer);

        User companyUser = new User();
        companyUser.setId(COMPANY_USER_ID);
        companyUser.setEmail("empresa@teste.com");
        companyUser.setType(UserType.COMPANY);

        when(userRepository.findById(COMPANY_USER_ID)).thenReturn(Optional.of(companyUser));
        when(userRepository.findById(PROFESSIONAL_USER_ID)).thenReturn(Optional.of(professionalUser));
        when(companyService.findByUserId(COMPANY_USER_ID)).thenReturn(Optional.of(company));
        when(professionalService.findByUserId(PROFESSIONAL_USER_ID)).thenReturn(Optional.of(professional));
        when(screeningInvitationRepository
                .findByScreeningStageScreeningQuestionnaireProjectCompanyId(COMPANY_ID))
                .thenReturn(List.of(invitation));
        when(screeningInvitationRepository.findByProfessionalId(PROFESSIONAL_ID))
                .thenReturn(List.of(invitation));
        when(screeningVideoService.exportPlaybackUrl(any(ScreeningAnswer.class))).thenReturn(SIGNED_URL);
        when(screeningVideoService.exportUrlTtlSeconds()).thenReturn(604800);
    }

    @Test
    void companyExportCarriesNoCandidateVideo() {
        Map<String, Object> export = exportService.export(COMPANY_USER_ID);

        assertNotNull(export.get("screeningProcesses"), "a empresa continua exportando os processos dela");

        String serialized = export.toString();
        assertFalse(serialized.contains(OBJECT_URL), "a URL do objeto nunca sai do backend");
        assertFalse(serialized.contains(SIGNED_URL), "nem um link assinado");
        assertFalse(serialized.contains("downloadUrl"),
                "imagem e voz do candidato não são dado que a empresa porta consigo (Rule 2)");
    }

    @Test
    void professionalExportCarriesTheirOwnVideo() {
        Map<String, Object> export = exportService.export(PROFESSIONAL_USER_ID);

        String serialized = export.toString();
        assertTrue(serialized.contains(SIGNED_URL), "é dado do titular -- tem que sair no export dele");
        assertTrue(serialized.contains("downloadUrlValidForSeconds"),
                "o link é temporário, e o export tem que dizer isso");
    }

    @Test
    void professionalExportSaysWhenTheFileIsAlreadyGone() {
        invitation.getAnswers().get(0).setVideoUrl(null);
        when(screeningVideoService.exportPlaybackUrl(any(ScreeningAnswer.class))).thenReturn(null);

        Map<String, Object> export = exportService.export(PROFESSIONAL_USER_ID);

        String serialized = export.toString();
        assertTrue(serialized.contains("removido"),
                "conta já excluída -> referência clara, não um campo que simplesmente some");
        assertFalse(serialized.contains("downloadUrl"));
    }

    @Test
    void videoBlockIsAbsentForNonVideoAnswers() {
        ScreeningQuestion essayQuestion = new ScreeningQuestion();
        essayQuestion.setId(78L);
        essayQuestion.setScreeningStage(invitation.getScreeningStage());
        essayQuestion.setType(ScreeningQuestionType.ESSAY);
        essayQuestion.setPrompt("Descreva um desafio");
        essayQuestion.setActive(true);

        invitation.getAnswers().clear();
        ScreeningAnswer essay = new ScreeningAnswer();
        essay.setScreeningInvitation(invitation);
        essay.setScreeningQuestion(essayQuestion);
        essay.setEssayText("resposta em texto");
        invitation.getAnswers().add(essay);

        Map<String, Object> export = exportService.export(PROFESSIONAL_USER_ID);

        String serialized = export.toString();
        assertTrue(serialized.contains("resposta em texto"));
        assertTrue(serialized.contains("video=null"),
                "questão dissertativa não ganha bloco de vídeo populado");
    }

    @Test
    void anonymousLookupOfTheCompanyStillWorks() {
        // Sanidade: o export da empresa é montado sem tocar em nada de vídeo -- se algum dia
        // alguém plugar videoExport() em screeningForCompany, este teste e o primeiro caem juntos.
        Map<String, Object> export = exportService.export(COMPANY_USER_ID);
        assertFalse(export.toString().contains("durationSeconds"));
        verifyNoVideoSigning();
    }

    private void verifyNoVideoSigning() {
        org.mockito.Mockito.verify(screeningVideoService, org.mockito.Mockito.never())
                .exportPlaybackUrl(any(ScreeningAnswer.class));
    }

}
