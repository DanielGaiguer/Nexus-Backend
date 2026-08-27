package com.main.nexus.service;

import com.main.nexus.dto.NotificationDTO;
import com.main.nexus.dto.NotificationSummaryDTO;
import com.main.nexus.model.Notification;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.NotificationType;
import com.main.nexus.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final int OLD_NOTIFICATION_DAYS = 30;

    @Autowired
    private NotificationRepository notificationRepository;

    // API PÚBLICA — criação de notificações

    public void notify(User user, NotificationType type,
                       String title, String message, String actionUrl) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setActionUrl(actionUrl);
        notificationRepository.save(notification);
    }

    // MÉTODOS DE FÁBRICA — um por tipo de evento
    // Centralizam o texto das notificações num único luga
    @Async
    public void notifyNewInvite(User professional,
                                String companyName, String projectTitle,
                                Long matchId) {
        notify(
            professional,
            NotificationType.NEW_INVITE,
            "Novo convite recebido",
            companyName + " demonstrou interesse no seu perfil para o projeto \"" + projectTitle + "\".",
            "/matches/" + matchId
        );
    }
    @Async
    public void notifyNewInterestReceived(User companyUser,
                                          String professionalName, String projectTitle,
                                          Long matchId) {
        notify(
            companyUser,
            NotificationType.NEW_INTEREST_RECEIVED,
            "Profissional demonstrou interesse",
            professionalName + " demonstrou interesse no seu projeto \"" + projectTitle + "\".",
            "/projects/ranking/" + matchId
        );
    }
    @Async
    public void notifyMatchConfirmed(User user,
                                     String otherPartyName, String projectTitle,
                                     Long matchId) {
        notify(
            user,
            NotificationType.MATCH_CONFIRMED,
            "Match confirmado!",
            "Seu match com " + otherPartyName + " no projeto \"" + projectTitle + "\" foi confirmado. Os contatos já estão disponíveis.",
            "/matches/" + matchId
        );
    }
    @Async
    public void notifyMatchCancelled(User user,
                                     String otherPartyName, String projectTitle) {
        notify(
            user,
            NotificationType.MATCH_CANCELLED,
            "Match cancelado",
            otherPartyName + " cancelou o match confirmado no projeto \"" + projectTitle + "\".",
            "/matches"
        );
    }
    @Async
    public void notifyInviteCancelled(User user,
                                      String companyName, String projectTitle) {
        notify(
            user,
            NotificationType.INVITE_REJECTED,
            "Convite cancelado",
            companyName + " cancelou o convite enviado para o projeto \"" + projectTitle + "\".",
            "/matches"
        );
    }
    @Async
    public void notifyInterestWithdrawn(User companyUser,
                                        String professionalName, String projectTitle) {
        notify(
            companyUser,
            NotificationType.INVITE_REJECTED,
            "Interesse retirado",
            professionalName + " retirou o interesse demonstrado no projeto \"" + projectTitle + "\".",
            "/matches"
        );
    }
    @Async
    public void notifyInviteRejected(User user,
                                     String otherPartyName, String projectTitle) {
        notify(
            user,
            NotificationType.INVITE_REJECTED,
            "Convite recusado",
            otherPartyName + " recusou o convite para o projeto \"" + projectTitle + "\".",
            "/matches"
        );
    }
    @Async
    public void notifyHighScoreOpportunity(User professional,
                                           String projectTitle, String companyName,
                                           double score, Long projectId) {
        notify(
            professional,
            NotificationType.HIGH_SCORE_OPPORTUNITY,
            "Oportunidade muito compatível!",
            String.format("O projeto \"%s\" do contratante %s tem %.0f%% de compatibilidade com o seu perfil.",
                    projectTitle, companyName, score),
            "/opportunities/" + projectId
        );
    }
    @Async
    public void notifyHighScoreCandidate(User companyUser,
                                         String professionalName, String projectTitle,
                                         double score, Long projectId) {
        notify(
            companyUser,
            NotificationType.HIGH_SCORE_CANDIDATE,
            "Novo candidato muito compatível!",
            String.format("%s tem %.0f%% de compatibilidade com o seu projeto \"%s\".",
                    professionalName, score, projectTitle),
            "/company/projects/" + projectId + "/ranking"
        );
    }
    @Async
    public void notifyNewCompanyRegistration(User adminUser, String companyName, Long companyId) {
        notify(
            adminUser,
            NotificationType.NEW_COMPANY_REGISTRATION,
            "Novo contratante aguardando aprovação",
            "O contratante \"" + companyName + "\" se cadastrou e está aguardando sua análise.",
            "/admin/company/" + companyId
        );
    }
    @Async
    public void notifyCompanyApproved(User companyUser, String companyName) {
        notify(
            companyUser,
            NotificationType.COMPANY_APPROVED,
            "Cadastro aprovado!",
            "Parabéns! O cadastro do contratante \"" + companyName + "\" foi aprovado. Você já pode publicar vagas e encontrar profissionais.",
            "/company/dashboard"
        );
    }
    @Async
    public void notifyCompanyRejected(User companyUser, String companyName) {
        notify(
            companyUser,
            NotificationType.COMPANY_REJECTED,
            "Cadastro não aprovado",
            "O cadastro do contratante \"" + companyName + "\" não foi aprovado pelo administrador. Entre em contato para mais informações.",
            null
        );
    }
    @Async
    public void notifyMatchStatusCheck(User companyUser,
                                       String professionalName, String projectTitle,
                                       Long matchId) {
        notify(
            companyUser,
            NotificationType.MATCH_STATUS_CHECK,
            "Como está indo o match?",
            "Seu match com " + professionalName + " no projeto \"" + projectTitle + "\" completa 30 dias em 15 dias. Que tal nos contar como está sendo?",
            "/matches/" + matchId + "/status-check"
        );
    }
    @Async
    public void notifyMatchExpiredForCompany(User companyUser,
                                             String professionalName, String projectTitle,
                                             Long matchId) {
        notify(
            companyUser,
            NotificationType.MATCH_EXPIRED_REVIEW_REQUEST,
            "Chegou a hora do feedback!",
            "Seu match com " + professionalName + " no projeto \"" + projectTitle + "\" completou 30 dias. Compartilhe como foi a experiência.",
            "/matches/" + matchId + "/review"
        );
    }
    @Async
    public void notifyMatchExpiredForProfessional(User professionalUser,
                                                  String companyName, String projectTitle,
                                                  Long matchId) {
        notify(
            professionalUser,
            NotificationType.MATCH_EXPIRED_REVIEW_REQUEST,
            "Como foi sua experiência?",
            "Seu match com " + companyName + " no projeto \"" + projectTitle + "\" completou 30 dias. Que tal avaliar como foi?",
            "/matches/" + matchId + "/review"
        );
    }
    @Async
    public void notifyProjectClosed(User professionalUser,
                                    String projectTitle, String companyName) {
        notify(
            professionalUser,
            NotificationType.PROJECT_CLOSED,
            "Vaga encerrada",
            "A vaga \"" + projectTitle + "\" do contratante " + companyName + " foi encerrada.",
            "/opportunities"
        );
    }
    @Async
    public void notifyProjectPositionsFull(User companyUser, String projectTitle, Long projectId) {
        notify(
            companyUser,
            NotificationType.PROJECT_POSITIONS_FULL,
            "Limite de vagas atingido",
            "O projeto \"" + projectTitle + "\" atingiu o limite de vagas e foi pausado automaticamente. " +
            "Acesse Meus Projetos para encerrar ou reabrir com mais vagas.",
            "/company/projects/" + projectId
        );
    }
    @Async
    public void notifyProjectAddedToPortfolio(User professionalUser,
                                              String companyName, String projectTitle,
                                              boolean completed) {
        String situacao = completed
                ? "que o projeto \"" + projectTitle + "\" foi concluído"
                : "que vocês estão trabalhando juntos no projeto \"" + projectTitle + "\"";
        notify(
            professionalUser,
            NotificationType.PROJECT_ADDED_TO_PORTFOLIO,
            "Projeto adicionado ao seu portfólio",
            companyName + " enviou um relatório confirmando " + situacao +
            ". Ele foi adicionado automaticamente ao seu portfólio.",
            "/pro/portfolio"
        );
    }
    @Async
    public void notifyProjectClosedByAdmin(User companyUser, String projectTitle) {
        notify(
            companyUser,
            NotificationType.PROJECT_CLOSED_BY_ADMIN,
            "Oportunidade encerrada pelo administrador",
            "A sua oportunidade \"" + projectTitle + "\" foi encerrada por um Administrador. " +
            "Para mais informações, entre em contato com admin@gmail.com.",
            "/company/dashboard"
        );
    }
    
    // Disparado por ProfessionalInactivityService quando o profissional passa 1 mês sem
    // logar e é marcado indisponível automaticamente.
    @Async
    public void notifyAccountMarkedUnavailable(User user) {
        notify(
            user,
            NotificationType.ACCOUNT_MARKED_UNAVAILABLE,
            "Sua conta foi marcada como indisponível",
            "Faz um mês que você não acessa o Nexus, então marcamos sua conta como indisponível — " +
            "você deixou de participar do cálculo de compatibilidade com as oportunidades. " +
            "Para voltar a aparecer nos rankings, acesse seu perfil e marque-se como disponível novamente.",
            "/pro/profile"
        );
    }

    @Async
    public void notifyProposalReceived(User companyUser,
                                       String professionalName, String projectTitle,
                                       Long projectId) {
        notify(
            companyUser,
            NotificationType.PROPOSAL_RECEIVED,
            "Nova proposta recebida",
            professionalName + " enviou uma proposta para o seu projeto \"" + projectTitle + "\".",
            "/company/projects/" + projectId + "/proposals"
        );
    }
    @Async
    public void notifyProposalAccepted(User professionalUser,
                                       String companyName, String projectTitle,
                                       Long matchId) {
        notify(
            professionalUser,
            NotificationType.PROPOSAL_ACCEPTED,
            "Proposta aceita!",
            companyName + " aceitou sua proposta para o projeto \"" + projectTitle + "\". O match foi confirmado e os contatos já estão disponíveis.",
            "/matches/" + matchId
        );
    }
    @Async
    public void notifyProposalRejected(User professionalUser,
                                       String companyName, String projectTitle) {
        notify(
            professionalUser,
            NotificationType.PROPOSAL_REJECTED,
            "Proposta recusada",
            companyName + " recusou sua proposta para o projeto \"" + projectTitle + "\".",
            "/pro/opportunities"
        );
    }
    @Async
    public void notifyProposalPositionFilled(User professionalUser,
                                             String companyName, String projectTitle) {
        notify(
            professionalUser,
            NotificationType.PROPOSAL_POSITION_FILLED,
            "Vaga preenchida",
            "O projeto \"" + projectTitle + "\" de " + companyName + " já teve suas vagas preenchidas por outra proposta. Sua proposta não segue mais em análise.",
            "/pro/opportunities"
        );
    }
    @Async
    public void notifyProposalExpired(User professionalUser, String projectTitle) {
        notify(
            professionalUser,
            NotificationType.PROPOSAL_EXPIRED,
            "Sua proposta expirou",
            "O prazo de validade da sua proposta para o projeto \"" + projectTitle + "\" terminou sem resposta do contratante. Você pode enviar uma nova proposta se ainda tiver interesse.",
            "/pro/opportunities"
        );
    }

    // Disparado pelo gate (ScreeningInvitationService.checkGate) quando o profissional tenta
    // demonstrar interesse, enviar proposta ou aceitar um convite numa vaga com questionário de
    // triagem vinculado, e ainda não tem uma tentativa concluída -- a ação original só se
    // completa depois que ele responder.
    @Async
    public void notifyScreeningInvitationReceived(User professionalUser,
                                                  String projectTitle, String stageTitle, Long invitationId) {
        notify(
            professionalUser,
            NotificationType.SCREENING_INVITATION_RECEIVED,
            "Responda para continuar",
            "Para seguir com o projeto \"" + projectTitle + "\", responda a etapa \"" + stageTitle +
            "\" do processo seletivo.",
            "/pro/screening-invitations/" + invitationId + "/take"
        );
    }
    @Async
    public void notifyScreeningSubmitted(User companyUser,
                                         String professionalName, String projectTitle, String stageTitle,
                                         Long invitationId) {
        notify(
            companyUser,
            NotificationType.SCREENING_SUBMITTED,
            "Etapa do processo seletivo respondida",
            professionalName + " respondeu a etapa \"" + stageTitle + "\" do projeto \"" + projectTitle +
            "\" -- avalie para aprovar ou reprovar o avanço.",
            "/company/screening-invitations/" + invitationId
        );
    }
    @Async
    public void notifyScreeningDeclined(User companyUser,
                                        String professionalName, String projectTitle, String stageTitle) {
        notify(
            companyUser,
            NotificationType.SCREENING_DECLINED,
            "Etapa do processo seletivo recusada",
            professionalName + " recusou a etapa \"" + stageTitle + "\" do projeto \"" + projectTitle + "\".",
            "/company/matches"
        );
    }
    @Async
    public void notifyScreeningStageApproved(User professionalUser,
                                             String projectTitle, String stageTitle,
                                             Long nextInvitationId) {
        notify(
            professionalUser,
            NotificationType.SCREENING_STAGE_APPROVED,
            "Você avançou de etapa!",
            "Você foi aprovado na etapa \"" + stageTitle + "\" do processo seletivo do projeto \"" +
            projectTitle + "\" -- responda a próxima etapa para continuar.",
            "/pro/screening-invitations/" + nextInvitationId + "/take"
        );
    }
    // Aprovou a ÚLTIMA etapa, mas a ação que ficou pendente não se resolve sozinha em MATCHED --
    // vira um match aguardando o aceite da empresa (interesse do profissional que a empresa ainda
    // não tinha retribuído). Sem isso, o profissional nunca fica sabendo que terminou de
    // responder tudo (notifyScreeningStageApproved só dispara quando existe uma PRÓXIMA etapa).
    @Async
    public void notifyScreeningApprovedAwaitingMatchDecision(User professionalUser, String projectTitle) {
        notify(
            professionalUser,
            NotificationType.SCREENING_STAGE_APPROVED,
            "Você concluiu o processo seletivo!",
            "Você foi aprovado em todas as etapas do processo seletivo do projeto \"" + projectTitle +
            "\" -- a decisão final sobre o match agora é da empresa.",
            "/pro/matches"
        );
    }
    // Espelho de notifyScreeningApprovedAwaitingMatchDecision pro caso de PROPOSAL_SUBMIT -- a
    // proposta nunca é aceita/recusada automaticamente pelo resultado da triagem (decisão
    // confirmada com o usuário), então o profissional também precisa ser avisado aqui.
    @Async
    public void notifyScreeningApprovedAwaitingProposalDecision(User professionalUser, String projectTitle) {
        notify(
            professionalUser,
            NotificationType.SCREENING_STAGE_APPROVED,
            "Você concluiu o processo seletivo!",
            "Você foi aprovado em todas as etapas do processo seletivo do projeto \"" + projectTitle +
            "\" -- a decisão final sobre sua proposta agora é da empresa.",
            "/pro/proposals"
        );
    }
    @Async
    public void notifyScreeningStageReproved(User professionalUser,
                                             String projectTitle, String stageTitle,
                                             Long invitationId) {
        notify(
            professionalUser,
            NotificationType.SCREENING_STAGE_REPROVED,
            "Resultado do processo seletivo",
            "Você não avançou na etapa \"" + stageTitle + "\" do processo seletivo do projeto \"" +
            projectTitle + "\".",
            "/pro/screening-invitations/" + invitationId
        );
    }
    @Async
    public void notifyScreeningExpiredForCompany(User companyUser,
                                                 String professionalName, String projectTitle, String stageTitle) {
        notify(
            companyUser,
            NotificationType.SCREENING_EXPIRED,
            "Etapa do processo seletivo expirou sem resposta",
            "A etapa \"" + stageTitle + "\" do projeto \"" + projectTitle + "\" (candidato " + professionalName +
            ") expirou sem resposta.",
            "/company/matches"
        );
    }
    @Async
    public void notifyScreeningExpiredForProfessional(User professionalUser, String projectTitle, String stageTitle) {
        notify(
            professionalUser,
            NotificationType.SCREENING_EXPIRED,
            "Uma etapa do processo seletivo expirou",
            "O prazo para responder a etapa \"" + stageTitle + "\" do projeto \"" + projectTitle +
            "\" terminou sem submissão. Não é mais possível responder ou tentar novamente para esta vaga.",
            "/pro/matches"
        );
    }

    // Disparado quando uma tentativa ainda pendente (SENT/IN_PROGRESS/SUBMITTED) é cancelada
    // porque o match ou o projeto associado foi encerrado -- ver
    // ScreeningInvitationService.cancelPendingForProfessionalProject/cancelAllPendingForProject,
    // chamados a partir de MatchService em todo caminho que encerra um match ou fecha um projeto.
    @Async
    public void notifyScreeningCancelled(User professionalUser, String projectTitle) {
        notify(
            professionalUser,
            NotificationType.SCREENING_CANCELLED,
            "Etapa do processo seletivo cancelada",
            "O processo seletivo do projeto \"" + projectTitle + "\" foi encerrado, e sua etapa pendente foi cancelada.",
            "/pro/matches"
        );
    }

    @Async
    public void notifyIncompleteProfile(User user, List<String> missingFields) {
        String fieldList = String.join(", ", missingFields);
        notify(
            user,
            NotificationType.COMPLETE_YOUR_PROFILE,
            "Complete seu perfil para aparecer nas recomendações",
            "Seu perfil ainda está incompleto. Para aparecer nos rankings e receber oportunidades compatíveis, preencha: " + fieldList + ".",
            "/pro/profile"
        );
    }

    @Async
    public void notifyIncompleteCompanyProfile(User user, List<String> missingFields) {
        String fieldList = String.join(", ", missingFields);
        notify(
            user,
            NotificationType.COMPLETE_YOUR_PROFILE,
            "Complete seu perfil de contratante para melhorar seus matches",
            "Seu perfil de contratante ainda está incompleto. Para melhorar a qualidade dos matches com profissionais, preencha: " + fieldList + ".",
            "/company/profile"
        );
    }

    // CONSULTAS — consumidas pelo controller

    public NotificationSummaryDTO getSummary(Long userId) {
        long unread = notificationRepository.countByUserIdAndReadFalse(userId);
        List<Notification> all = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
        return new NotificationSummaryDTO(unread, all.stream().map(this::toDTO).toList());
    }

    public List<NotificationDTO> getUnread(Long userId) {
        return notificationRepository
                .findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }
    
    // Precisam de @Transactional porque delegam a queries @Modifying — o Spring Data JPA exige uma transação ativa para executar UPDATE/DELETE via
    // JPQL (diferente de um save() normal via JpaRepository, que gerencia sua própria transação implicitamente através do proxy do repositório).

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        notificationRepository.markAsReadByIdAndUserId(notificationId, userId);
    }

    // chamado pelo job noturno
    @Transactional
    public void deleteAllOldNotifications() {
        notificationRepository.deleteAllOldReadNotifications(
                java.time.LocalDateTime.now().minusDays(OLD_NOTIFICATION_DAYS)
        );
    }

    // CONVERSÃO

    private NotificationDTO toDTO(Notification n) {
        return new NotificationDTO(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getActionUrl(),
                n.getRead(),
                n.getCreatedAt()
        );
    }
}