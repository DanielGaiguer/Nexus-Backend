package com.main.nexus.service;

import com.main.nexus.dto.ScreeningVideoConfirmRequestDTO;
import com.main.nexus.dto.ScreeningVideoPlaybackDTO;
import com.main.nexus.dto.ScreeningVideoUploadTicketDTO;
import com.main.nexus.model.Professional;
import com.main.nexus.model.ScreeningAnswer;
import com.main.nexus.model.ScreeningInvitation;
import com.main.nexus.model.ScreeningQuestion;
import com.main.nexus.model.enums.ScreeningInvitationStatus;
import com.main.nexus.model.enums.ScreeningQuestionType;
import com.main.nexus.repository.ScreeningInvitationRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Resposta em vídeo assíncrona. Três coisas vivem aqui, e nenhuma delas passa o arquivo pelo
// backend:
//
//  1. CONSENTIMENTO DE GRAVAÇÃO (acceptRecordingConsent) -- porta de entrada obrigatória. Sem
//     ele nenhuma URL de upload é assinada.
//  2. UPLOAD (createUploadTicket + confirmUpload) -- o backend assina, o browser sobe direto pro
//     Supabase, o backend confirma e mede o tamanho real. Isso evita o duplo buffer (Next +
//     Spring) que os outros três uploads do sistema têm, e que com 50MB de vídeo derrubaria o
//     backend.
//  3. PLAYBACK (playbackUrl) -- signed URL de curta validade, sob guard.
//
// A resposta de vídeo é a ÚNICA que nasce antes do submit: o ScreeningAnswer é criado aqui, na
// confirmação do upload, e o submit apenas encontra a linha pronta (ver
// ScreeningInvitationService.submit). Foi de propósito -- se o vídeo fosse parte do submit, um
// upload que falhasse derrubaria junto todas as outras respostas da etapa.
@Service
public class ScreeningVideoService {

    // Texto exibido ao candidato e gravado por extenso junto com o aceite. Mudar este texto NÃO
    // altera consentimentos já registrados: cada linha guarda o texto que aquela pessoa leu.
    public static final String RECORDING_CONSENT_TEXT =
            "Autorizo a gravação da minha imagem e da minha voz para responder a esta etapa do "
          + "processo seletivo. Entendo que o vídeo fica disponível apenas para a empresa "
          + "responsável por esta vaga e para mim, que não é usado para nenhuma outra finalidade "
          + "nem compartilhado com terceiros, e que posso excluí-lo excluindo minha conta.";

    @Autowired
    private ScreeningInvitationRepository screeningInvitationRepository;

    @Autowired
    private ScreeningInvitationService screeningInvitationService;

    @Autowired
    private SupabaseStorageService storageService;

    @Value("${nexus.screening.video.max-size-bytes}")
    private long maxSizeBytes;

    @Value("${nexus.screening.video.upload-url-ttl-seconds}")
    private int uploadUrlTtlSeconds;

    @Value("${nexus.screening.video.playback-url-ttl-seconds}")
    private int playbackUrlTtlSeconds;

    @Value("${nexus.screening.video.export-url-ttl-seconds}")
    private int exportUrlTtlSeconds;

    // ── consentimento ──────────────────────────────────────────────────

    @Transactional
    public ScreeningInvitation acceptRecordingConsent(Long invitationId, Long professionalId) {
        ScreeningInvitation invitation = screeningInvitationService.findById(invitationId);
        assertOwnedByProfessional(invitation, professionalId);
        assertAnswerable(invitation);

        // Idempotente: reabrir a tela de gravação não deve reescrever a data do aceite original,
        // que é justamente o que se quer poder provar depois.
        if (invitation.getVideoConsentAcceptedAt() != null) {
            return invitation;
        }

        invitation.setVideoConsentAcceptedAt(LocalDateTime.now());
        invitation.setVideoConsentText(RECORDING_CONSENT_TEXT);
        return screeningInvitationRepository.save(invitation);
    }

    // ── upload ─────────────────────────────────────────────────────────

    public ScreeningVideoUploadTicketDTO createUploadTicket(
            Long invitationId, Long questionId, Long professionalId, String contentType) {

        ScreeningInvitation invitation = screeningInvitationService.findById(invitationId);
        assertOwnedByProfessional(invitation, professionalId);
        assertAnswerable(invitation);
        ScreeningQuestion question = findVideoQuestion(invitation, questionId);

        if (invitation.getVideoConsentAcceptedAt() == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "Recording consent is required before uploading a video answer.");
        }

        SupabaseStorageService.VideoUploadTicket ticket = storageService
                .createScreeningVideoUploadTicket(
                        invitation.getId(), question.getId(), contentType, uploadUrlTtlSeconds);

        return new ScreeningVideoUploadTicketDTO(
                question.getId(),
                ticket.uploadUrl(),
                ticket.token(),
                ticket.objectUrl(),
                maxSizeBytes,
                uploadUrlTtlSeconds);
    }

    @Transactional
    public ScreeningAnswer confirmUpload(
            Long invitationId, Long professionalId, ScreeningVideoConfirmRequestDTO request) {

        ScreeningInvitation invitation = screeningInvitationService.findById(invitationId);
        assertOwnedByProfessional(invitation, professionalId);
        assertAnswerable(invitation);
        ScreeningQuestion question = findVideoQuestion(invitation, request.questionId());

        if (request.videoUrl() == null || request.videoUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), "'videoUrl' is required.");
        }

        // Teto real, medido no Supabase -- o `videoUrl` chega do client e o tamanho declarado
        // por ele não vale nada. Se o arquivo estourou o teto, ele é APAGADO aqui mesmo: deixá-lo
        // no bucket seria pagar armazenamento por um arquivo que nunca vai poder ser assistido.
        Long actualSize = storageService.screeningVideoSizeBytes(request.videoUrl());
        if (actualSize == null) {
            // Objeto ausente ou metadados indisponíveis. Não dá pra distinguir "upload não
            // chegou" de "a consulta falhou", e aceitar às cegas criaria uma resposta que aponta
            // pro nada -- então recusa, e o candidato tenta de novo.
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "The uploaded video could not be verified. Please try recording again.");
        }
        if (actualSize > maxSizeBytes) {
            storageService.deleteScreeningVideo(request.videoUrl());
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Video size must not exceed " + (maxSizeBytes / (1024 * 1024)) + "MB.");
        }
        storageService.validateScreeningVideoSize(actualSize, maxSizeBytes);

        ScreeningAnswer answer = existingAnswer(invitation, question.getId());
        if (answer == null) {
            answer = new ScreeningAnswer();
            answer.setScreeningInvitation(invitation);
            answer.setScreeningQuestion(question);
            invitation.getAnswers().add(answer);
        } else if (answer.getVideoUrl() != null && !answer.getVideoUrl().equals(request.videoUrl())) {
            // Regravou: o arquivo anterior vira lixo no bucket na hora, não "algum dia".
            storageService.deleteScreeningVideo(answer.getVideoUrl());
        }

        answer.setVideoUrl(request.videoUrl());
        answer.setVideoDurationSeconds(request.durationSeconds());
        screeningInvitationRepository.save(invitation);
        return answer;
    }

    // ── playback ───────────────────────────────────────────────────────

    // Guard: o próprio candidato OU a empresa dona da vaga. Qualquer outro chega em 403 antes de
    // qualquer coisa ser assinada -- a signed URL só existe depois que o acesso foi decidido.
    public ScreeningVideoPlaybackDTO playbackUrl(
            Long invitationId, Long questionId, Long companyId, Long professionalId) {

        ScreeningInvitation invitation = screeningInvitationService.findById(invitationId);
        screeningInvitationService.validateParticipant(invitation, companyId, professionalId);

        ScreeningAnswer answer = existingAnswer(invitation, questionId);
        if (answer == null || answer.getVideoUrl() == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404),
                    "There is no video answer for this question.");
        }

        String url = storageService.signScreeningVideoUrl(answer.getVideoUrl(), playbackUrlTtlSeconds);
        return new ScreeningVideoPlaybackDTO(
                questionId, url, playbackUrlTtlSeconds, answer.getVideoDurationSeconds());
    }

    // ── LGPD ───────────────────────────────────────────────────────────

    // Exclusao de conta: apaga os ARQUIVOS de video do profissional e zera o ponteiro, mas
    // MANTEM a linha ScreeningAnswer e todo o resto dela.
    //
    // Por que isso e um metodo separado, e nao uma mudanca no tratamento geral de respostas de
    // triagem: AccountDeletionService classifica resposta de triagem como categoria (c) --
    // integridade de dado de OUTRA pessoa, preservada -- e o argumento que sustenta essa
    // classificacao e "anonimizar a identidade em User/Professional ja resolve a exposicao".
    // Esse argumento vale pro texto de uma dissertativa e NAO vale pra um video: rosto e voz
    // identificam a pessoa sozinhos, independente do nome no banco. Entao o arquivo segue a
    // regra de curriculo/foto/anexo (apagado), e a linha segue a regra da categoria (c)
    // (preservada). videoDurationSeconds fica de proposito: sem ele, a empresa veria a resposta
    // sumir sem explicacao, em vez de "havia um video, foi removido".
    @Transactional
    public int purgeVideosForProfessional(Long professionalId) {
        int removed = 0;
        for (ScreeningInvitation invitation
                : screeningInvitationRepository.findByProfessionalId(professionalId)) {
            boolean touched = false;
            for (ScreeningAnswer answer : invitation.getAnswers()) {
                if (answer.getVideoUrl() == null) {
                    continue;
                }
                storageService.deleteScreeningVideo(answer.getVideoUrl());
                answer.setVideoUrl(null);
                touched = true;
                removed++;
            }
            if (touched) {
                screeningInvitationRepository.save(invitation);
            }
        }
        return removed;
    }

    // Link do video pro export de dados do PROPRIO titular. TTL longo de proposito -- o export e
    // um arquivo baixado, e um link de 5 minutos dentro dele nao serviria pra nada. Devolve null
    // quando nao ha video (ou quando ele ja foi removido por exclusao de conta), e o export
    // registra isso explicitamente em vez de omitir o campo.
    public String exportPlaybackUrl(ScreeningAnswer answer) {
        if (answer == null || answer.getVideoUrl() == null) {
            return null;
        }
        try {
            return storageService.signScreeningVideoUrl(answer.getVideoUrl(), exportUrlTtlSeconds);
        } catch (RuntimeException e) {
            // Um export inteiro nao pode falhar porque um link nao pode ser assinado agora.
            return null;
        }
    }

    public int exportUrlTtlSeconds() {
        return exportUrlTtlSeconds;
    }

    // ── apoio ──────────────────────────────────────────────────────────

    private ScreeningAnswer existingAnswer(ScreeningInvitation invitation, Long questionId) {
        if (questionId == null) {
            return null;
        }
        for (ScreeningAnswer answer : invitation.getAnswers()) {
            if (answer.getScreeningQuestion() != null
                    && questionId.equals(answer.getScreeningQuestion().getId())) {
                return answer;
            }
        }
        return null;
    }

    private ScreeningQuestion findVideoQuestion(ScreeningInvitation invitation, Long questionId) {
        List<ScreeningQuestion> questions = invitation.getScreeningStage().getQuestions();
        for (ScreeningQuestion question : questions) {
            if (question.getId().equals(questionId)) {
                if (question.getType() != ScreeningQuestionType.VIDEO_RESPONSE) {
                    throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                            "Question " + questionId + " is not a video question.");
                }
                if (!Boolean.TRUE.equals(question.getActive())) {
                    throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                            "Question " + questionId + " is no longer part of this stage.");
                }
                return question;
            }
        }
        throw new ResponseStatusException(HttpStatusCode.valueOf(404),
                "Question " + questionId + " does not belong to this screening stage.");
    }

    private void assertOwnedByProfessional(ScreeningInvitation invitation, Long professionalId) {
        Professional professional = invitation.getProfessional();
        if (professionalId == null || professional == null
                || !professional.getId().equals(professionalId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This screening invitation does not belong to you.");
        }
    }

    // Gravar/regravar só enquanto a tentativa está mesmo aberta. Depois do envio o vídeo é
    // histórico -- e a empresa pode já tê-lo assistido.
    private void assertAnswerable(ScreeningInvitation invitation) {
        if (invitation.getStatus() != ScreeningInvitationStatus.SENT
                && invitation.getStatus() != ScreeningInvitationStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This screening invitation is no longer open for answers.");
        }
        if (LocalDateTime.now().isAfter(invitation.getDeadlineAt())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "The deadline to respond to this screening has passed.");
        }
    }
}
