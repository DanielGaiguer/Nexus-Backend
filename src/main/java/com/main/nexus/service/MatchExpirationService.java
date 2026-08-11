package com.main.nexus.service;

import com.main.nexus.model.Match;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.NotificationType;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MatchExpirationService {

    private static final int EXPIRATION_DAYS = 30;
    public static final int APPROACHING_THRESHOLD_DAYS = 14;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EmailService emailService;

    @Transactional
    public void checkAndExpireMatches() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(EXPIRATION_DAYS);

        List<Match> expiredMatches = matchRepository
                .findByStatusAndCreatedAtBeforeAndActiveTrue(StatusMatch.MATCHED, threshold);

        for (Match match : expiredMatches) {
            match.setActive(false);

            notificationService.notifyMatchExpiredForCompany(
                    match.getProject().getCompany().getUser(),
                    match.getProfessional().getName(),
                    match.getProject().getTitle(),
                    match.getId());

            notificationService.notifyMatchExpiredForProfessional(
                    match.getProfessional().getUser(),
                    match.getProject().getCompany().getCompanyName(),
                    match.getProject().getTitle(),
                    match.getId());
        }
        matchRepository.saveAll(expiredMatches);
    }

    // Sem teto superior de propósito: se o job não rodar num dia específico (deploy,
    // servidor fora do ar), o match não pode "passar batido" e ficar sem ser notificado pra sempre
    @Transactional
    public void checkAndNotifyApproaching() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(APPROACHING_THRESHOLD_DAYS);

        List<Match> approachingMatches = matchRepository.findByStatus(StatusMatch.MATCHED)
                .stream()
                .filter(m -> !Boolean.FALSE.equals(m.getActive())) // Se estiver ativo
                .filter(m -> m.getCreatedAt().isBefore(threshold)) // Se foi criado a 14 dias atras
                .toList(); // Lista eles

        for (Match match : approachingMatches) {
            User companyUser = match.getProject().getCompany().getUser();
            String actionUrl = "/matches/" + match.getId() + "/status-check";

            // Verifica se ja existe uma notifcacao no sistema com mesmo id da empresa, mesmo tipo de notificacao e mesmo URL de acao
            boolean alreadyNotified = notificationRepository.existsByUserIdAndTypeAndActionUrl(
                    companyUser.getId(), NotificationType.MATCH_STATUS_CHECK, actionUrl);

            // Caso nao foi notificado, notifica a empresa
            if (!alreadyNotified) {
                String professionalName = match.getProfessional().getName();
                String projectTitle = match.getProject().getTitle();

                notificationService.notifyMatchStatusCheck(
                        companyUser, professionalName, projectTitle, match.getId());

                emailService.send(
                        companyUser.getEmail(),
                        "Como está indo o match? — Nexus",
                        "Olá,\n\n" +
                        "Seu match com " + professionalName + " no projeto \"" + projectTitle + "\" completa 30 dias em breve. " +
                        "Que tal nos contar como está sendo?\n\n" +
                        "Acesse o Nexus e responda em poucos cliques.\n\nEquipe Nexus"
                );
            }
        }
    }
}
