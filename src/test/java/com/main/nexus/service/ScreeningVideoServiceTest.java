package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.main.nexus.dto.ScreeningVideoConfirmRequestDTO;
import com.main.nexus.dto.ScreeningVideoPlaybackDTO;
import com.main.nexus.dto.ScreeningVideoUploadTicketDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.ScreeningAnswer;
import com.main.nexus.model.ScreeningInvitation;
import com.main.nexus.model.ScreeningQuestion;
import com.main.nexus.model.ScreeningQuestionnaire;
import com.main.nexus.model.ScreeningStage;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.ScreeningInvitationStatus;
import com.main.nexus.model.enums.ScreeningQuestionType;
import com.main.nexus.model.enums.ScreeningStageKind;
import com.main.nexus.repository.ScreeningInvitationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

// Guards e limites do vídeo assíncrono (Prompt 2/7). O que importa aqui é que NADA seja assinado
// antes do acesso ser decidido, e que o teto de tamanho valha mesmo com o arquivo indo direto do
// browser pro Supabase.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScreeningVideoServiceTest {

    private static final Long COMPANY_ID = 10L;
    private static final Long OTHER_COMPANY_ID = 11L;
    private static final Long PROJECT_ID = 100L;
    private static final Long PROFESSIONAL_ID = 55L;
    private static final Long OTHER_PROFESSIONAL_ID = 56L;
    private static final Long INVITATION_ID = 900L;
    private static final Long QUESTION_ID = 77L;

    private static final long MAX_SIZE = 52_428_800L;
    private static final String OBJECT_URL =
            "https://sb.test/storage/v1/object/screening-videos-nexus/screenings/900/77/abc.webm";

    @Mock private ScreeningInvitationRepository screeningInvitationRepository;
    @Mock private ScreeningInvitationService screeningInvitationService;
    @Mock private SupabaseStorageService storageService;

    @InjectMocks private ScreeningVideoService videoService;

    private ScreeningInvitation invitation;
    private ScreeningQuestion videoQuestion;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(videoService, "maxSizeBytes", MAX_SIZE);
        ReflectionTestUtils.setField(videoService, "uploadUrlTtlSeconds", 900);
        ReflectionTestUtils.setField(videoService, "playbackUrlTtlSeconds", 300);
        ReflectionTestUtils.setField(videoService, "exportUrlTtlSeconds", 604800);

        Company company = new Company();
        company.setId(COMPANY_ID);

        Project project = new Project();
        project.setId(PROJECT_ID);
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
        questionnaire.getStages().add(stage);

        videoQuestion = new ScreeningQuestion();
        videoQuestion.setId(QUESTION_ID);
        videoQuestion.setScreeningStage(stage);
        videoQuestion.setType(ScreeningQuestionType.VIDEO_RESPONSE);
        videoQuestion.setPrompt("Fale sobre você");
        videoQuestion.setActive(true);
        stage.getQuestions().add(videoQuestion);

        User professionalUser = new User();
        professionalUser.setId(7L);
        Professional professional = new Professional();
        professional.setId(PROFESSIONAL_ID);
        professional.setName("Candidato");
        professional.setUser(professionalUser);

        invitation = new ScreeningInvitation();
        invitation.setId(INVITATION_ID);
        invitation.setScreeningStage(stage);
        invitation.setProfessional(professional);
        invitation.setStatus(ScreeningInvitationStatus.IN_PROGRESS);
        invitation.setSentAt(LocalDateTime.now().minusDays(1));
        invitation.setDeadlineAt(LocalDateTime.now().plusDays(2));

        when(screeningInvitationService.findById(INVITATION_ID)).thenReturn(invitation);
        when(screeningInvitationRepository.save(any(ScreeningInvitation.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(storageService.createScreeningVideoUploadTicket(anyLong(), anyLong(), anyString(), anyInt()))
                .thenReturn(new SupabaseStorageService.VideoUploadTicket(
                        "https://sb.test/upload", "tok", OBJECT_URL));
        when(storageService.signScreeningVideoUrl(anyString(), anyInt()))
                .thenReturn("https://sb.test/signed?token=x");
    }

    private void withConsent() {
        invitation.setVideoConsentAcceptedAt(LocalDateTime.now());
        invitation.setVideoConsentText(ScreeningVideoService.RECORDING_CONSENT_TEXT);
    }

    private ScreeningAnswer withUploadedVideo() {
        ScreeningAnswer answer = new ScreeningAnswer();
        answer.setScreeningInvitation(invitation);
        answer.setScreeningQuestion(videoQuestion);
        answer.setVideoUrl(OBJECT_URL);
        answer.setVideoDurationSeconds(42);
        invitation.getAnswers().add(answer);
        return answer;
    }

    // ── consentimento ──────────────────────────────────────────────────

    @Test
    void consentIsRecordedWithTheExactTextTheCandidateRead() {
        ScreeningInvitation saved = videoService.acceptRecordingConsent(INVITATION_ID, PROFESSIONAL_ID);

        assertNotNull(saved.getVideoConsentAcceptedAt());
        assertEquals(ScreeningVideoService.RECORDING_CONSENT_TEXT, saved.getVideoConsentText());
    }

    @Test
    void reacceptingConsentKeepsTheOriginalTimestamp() {
        withConsent();
        LocalDateTime original = invitation.getVideoConsentAcceptedAt();

        videoService.acceptRecordingConsent(INVITATION_ID, PROFESSIONAL_ID);

        assertEquals(original, invitation.getVideoConsentAcceptedAt(),
                "a data do aceite é o que se precisa poder provar depois -- não pode ser reescrita");
    }

    @Test
    void uploadUrlRequiresRecordingConsentFirst() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> videoService.createUploadTicket(
                        INVITATION_ID, QUESTION_ID, PROFESSIONAL_ID, "video/webm"));

        assertEquals(409, error.getStatusCode().value());
        verify(storageService, never())
                .createScreeningVideoUploadTicket(anyLong(), anyLong(), anyString(), anyInt());
    }

    // ── guard de upload ────────────────────────────────────────────────

    @Test
    void ownerGetsAnUploadTicket() {
        withConsent();

        ScreeningVideoUploadTicketDTO ticket = videoService.createUploadTicket(
                INVITATION_ID, QUESTION_ID, PROFESSIONAL_ID, "video/webm");

        assertEquals(QUESTION_ID, ticket.questionId());
        assertEquals("https://sb.test/upload", ticket.uploadUrl());
        assertEquals(MAX_SIZE, ticket.maxSizeBytes(), "o front recebe o teto pra barrar antes de subir");
    }

    @Test
    void anotherProfessionalCannotGetAnUploadTicket() {
        withConsent();

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> videoService.createUploadTicket(
                        INVITATION_ID, QUESTION_ID, OTHER_PROFESSIONAL_ID, "video/webm"));

        assertEquals(403, error.getStatusCode().value());
        verify(storageService, never())
                .createScreeningVideoUploadTicket(anyLong(), anyLong(), anyString(), anyInt());
    }

    @Test
    void expiredInvitationCannotBeRecorded() {
        withConsent();
        invitation.setDeadlineAt(LocalDateTime.now().minusMinutes(1));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> videoService.createUploadTicket(
                        INVITATION_ID, QUESTION_ID, PROFESSIONAL_ID, "video/webm"));

        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void submittedInvitationCannotBeRecorded() {
        withConsent();
        invitation.setStatus(ScreeningInvitationStatus.SUBMITTED);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> videoService.createUploadTicket(
                        INVITATION_ID, QUESTION_ID, PROFESSIONAL_ID, "video/webm"));

        assertEquals(400, error.getStatusCode().value());
    }

    // ── teto de tamanho ────────────────────────────────────────────────

    @Test
    void confirmCreatesTheAnswerWithinTheSizeCap() {
        withConsent();
        when(storageService.screeningVideoSizeBytes(OBJECT_URL)).thenReturn(1_000_000L);

        ScreeningAnswer answer = videoService.confirmUpload(INVITATION_ID, PROFESSIONAL_ID,
                new ScreeningVideoConfirmRequestDTO(QUESTION_ID, OBJECT_URL, 42));

        assertEquals(OBJECT_URL, answer.getVideoUrl());
        assertEquals(42, answer.getVideoDurationSeconds());
        assertEquals(1, invitation.getAnswers().size());
    }

    @Test
    void oversizedVideoIsRejectedAndDeletedFromStorage() {
        withConsent();
        // O tamanho vem MEDIDO no Supabase, não declarado pelo cliente -- o arquivo já subiu
        // direto, então esta é a única checagem que vale.
        when(storageService.screeningVideoSizeBytes(OBJECT_URL)).thenReturn(MAX_SIZE + 1);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> videoService.confirmUpload(INVITATION_ID, PROFESSIONAL_ID,
                        new ScreeningVideoConfirmRequestDTO(QUESTION_ID, OBJECT_URL, 42)));

        assertEquals(400, error.getStatusCode().value());
        assertTrue(error.getReason().contains("50MB"));
        verify(storageService).deleteScreeningVideo(OBJECT_URL);
        assertTrue(invitation.getAnswers().isEmpty(), "nada de resposta apontando pro arquivo recusado");
    }

    @Test
    void unverifiableUploadIsRejected() {
        withConsent();
        when(storageService.screeningVideoSizeBytes(OBJECT_URL)).thenReturn(null);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> videoService.confirmUpload(INVITATION_ID, PROFESSIONAL_ID,
                        new ScreeningVideoConfirmRequestDTO(QUESTION_ID, OBJECT_URL, 42)));

        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void rerecordingDeletesThePreviousFile() {
        withConsent();
        withUploadedVideo();
        String newUrl = OBJECT_URL.replace("abc", "def");
        when(storageService.screeningVideoSizeBytes(newUrl)).thenReturn(2_000_000L);

        videoService.confirmUpload(INVITATION_ID, PROFESSIONAL_ID,
                new ScreeningVideoConfirmRequestDTO(QUESTION_ID, newUrl, 30));

        verify(storageService).deleteScreeningVideo(OBJECT_URL);
        assertEquals(1, invitation.getAnswers().size(), "regravar substitui, não empilha");
        assertEquals(newUrl, invitation.getAnswers().get(0).getVideoUrl());
    }

    // ── guard de playback ──────────────────────────────────────────────

    @Test
    void ownerCanPlayBackTheirOwnVideo() {
        withUploadedVideo();

        ScreeningVideoPlaybackDTO playback =
                videoService.playbackUrl(INVITATION_ID, QUESTION_ID, null, PROFESSIONAL_ID);

        assertEquals("https://sb.test/signed?token=x", playback.url());
        assertEquals(300, playback.expiresInSeconds());
        assertEquals(42, playback.durationSeconds());
    }

    @Test
    void owningCompanyCanPlayBackTheVideo() {
        withUploadedVideo();
        // Delegado a ScreeningInvitationService.validateParticipant, que é onde a posse da vaga
        // já é conferida no resto do módulo -- aqui o mock representa "passou no guard".
        ScreeningVideoPlaybackDTO playback =
                videoService.playbackUrl(INVITATION_ID, QUESTION_ID, COMPANY_ID, null);

        assertNotNull(playback.url());
        verify(screeningInvitationService).validateParticipant(invitation, COMPANY_ID, null);
    }

    @Test
    void outsiderCannotPlayBackTheVideoAndNothingIsSigned() {
        withUploadedVideo();
        org.mockito.Mockito.doThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatusCode.valueOf(403), "nope"))
                .when(screeningInvitationService)
                .validateParticipant(any(), eq(OTHER_COMPANY_ID), any());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> videoService.playbackUrl(INVITATION_ID, QUESTION_ID, OTHER_COMPANY_ID, null));

        assertEquals(403, error.getStatusCode().value());
        verify(storageService, never()).signScreeningVideoUrl(anyString(), anyInt());
    }

    @Test
    void playbackOfAMissingVideoIs404() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> videoService.playbackUrl(INVITATION_ID, QUESTION_ID, null, PROFESSIONAL_ID));

        assertEquals(404, error.getStatusCode().value());
    }

    // ── exclusão de conta ──────────────────────────────────────────────

    @Test
    void accountDeletionRemovesTheFileButKeepsTheAnswerRow() {
        ScreeningAnswer answer = withUploadedVideo();
        when(screeningInvitationRepository.findByProfessionalId(PROFESSIONAL_ID))
                .thenReturn(List.of(invitation));

        int removed = videoService.purgeVideosForProfessional(PROFESSIONAL_ID);

        assertEquals(1, removed);
        verify(storageService).deleteScreeningVideo(OBJECT_URL);
        assertNull(answer.getVideoUrl(), "o ponteiro morre junto com o arquivo");
        assertEquals(1, invitation.getAnswers().size(),
                "a linha continua: respostas de triagem seguem na categoria (c)");
        assertEquals(42, answer.getVideoDurationSeconds(),
                "sobra o rastro de que existiu um vídeo -- não some sem explicação");
    }

    @Test
    void accountDeletionIgnoresAnswersWithoutVideo() {
        ScreeningAnswer essay = new ScreeningAnswer();
        essay.setScreeningInvitation(invitation);
        essay.setScreeningQuestion(videoQuestion);
        essay.setEssayText("texto");
        invitation.getAnswers().add(essay);
        when(screeningInvitationRepository.findByProfessionalId(PROFESSIONAL_ID))
                .thenReturn(List.of(invitation));

        assertEquals(0, videoService.purgeVideosForProfessional(PROFESSIONAL_ID));
        verify(storageService, never()).deleteScreeningVideo(anyString());
        assertEquals("texto", essay.getEssayText());
    }

    @Test
    void exportUrlIsNullOnceTheFileIsGone() {
        ScreeningAnswer answer = withUploadedVideo();
        assertNotNull(videoService.exportPlaybackUrl(answer));

        answer.setVideoUrl(null);
        assertNull(videoService.exportPlaybackUrl(answer),
                "conta já excluída -> o export precisa dizer que o arquivo foi removido");
    }
}
