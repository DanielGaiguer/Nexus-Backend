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

    @Autowired
    private NotificationRepository notificationRepository;

    // =========================================================
    // API PÚBLICA — criação de notificações
    // =========================================================

    @Async
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

    // =========================================================
    // MÉTODOS DE FÁBRICA — um por tipo de evento
    // Centralizam o texto das notificações num único lugar
    // =========================================================

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

    public void notifyNewReviewReceived(User user,
                                        String reviewerName, int rating) {
        String stars = "★".repeat(rating) + "☆".repeat(5 - rating);
        notify(
            user,
            NotificationType.NEW_REVIEW_RECEIVED,
            "Nova avaliação recebida",
            reviewerName + " avaliou você com " + stars + " (" + rating + "/5).",
            "/profile"
        );
    }

    public void notifyHighScoreOpportunity(User professional,
                                           String projectTitle, String companyName,
                                           double score, Long projectId) {
        notify(
            professional,
            NotificationType.HIGH_SCORE_OPPORTUNITY,
            "Oportunidade muito compatível!",
            String.format("O projeto \"%s\" da empresa %s tem %.0f%% de compatibilidade com o seu perfil.",
                    projectTitle, companyName, score),
            "/opportunities/" + projectId
        );
    }

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

    public void notifyNewCompanyRegistration(User adminUser, String companyName, Long companyId) {
        notify(
            adminUser,
            NotificationType.NEW_COMPANY_REGISTRATION,
            "Nova empresa aguardando aprovação",
            "A empresa \"" + companyName + "\" se cadastrou e está aguardando sua análise.",
            "/admin/company/" + companyId
        );
    }

    public void notifyCompanyApproved(User companyUser, String companyName) {
        notify(
            companyUser,
            NotificationType.COMPANY_APPROVED,
            "Cadastro aprovado!",
            "Parabéns! O cadastro da empresa \"" + companyName + "\" foi aprovado. Você já pode publicar vagas e encontrar profissionais.",
            "/company/dashboard"
        );
    }

    public void notifyCompanyRejected(User companyUser, String companyName) {
        notify(
            companyUser,
            NotificationType.COMPANY_REJECTED,
            "Cadastro não aprovado",
            "O cadastro da empresa \"" + companyName + "\" não foi aprovado pelo administrador. Entre em contato para mais informações.",
            null
        );
    }

    public void notifyProjectClosed(User professionalUser,
                                    String projectTitle, String companyName) {
        notify(
            professionalUser,
            NotificationType.PROJECT_CLOSED,
            "Vaga encerrada",
            "A vaga \"" + projectTitle + "\" da empresa " + companyName + " foi encerrada.",
            "/opportunities"
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
            "Complete o perfil da empresa para melhorar seus matches",
            "O perfil da sua empresa ainda está incompleto. Para melhorar a qualidade dos matches com profissionais, preencha: " + fieldList + ".",
            "/company/profile"
        );
    }

    // =========================================================
    // CONSULTAS — consumidas pelo controller
    // =========================================================

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

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        notificationRepository.markAsReadByIdAndUserId(notificationId, userId);
    }

    @Transactional
    public void deleteOldNotifications(Long userId) {
        notificationRepository.deleteOldReadNotifications(
                userId,
                java.time.LocalDateTime.now().minusDays(30)
        );
    }

    // =========================================================
    // CONVERSÃO
    // =========================================================

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