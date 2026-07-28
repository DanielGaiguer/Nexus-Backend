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
    private static final int APPROACHING_WINDOW_START_DAYS = 16;
    private static final int APPROACHING_WINDOW_END_DAYS = 14;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

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

    @Transactional
    public void checkAndNotifyApproaching() {
        LocalDateTime windowStart = LocalDateTime.now().minusDays(APPROACHING_WINDOW_START_DAYS);
        LocalDateTime windowEnd = LocalDateTime.now().minusDays(APPROACHING_WINDOW_END_DAYS);

        List<Match> approachingMatches = matchRepository.findByStatus(StatusMatch.MATCHED)
                .stream()
                .filter(m -> !Boolean.FALSE.equals(m.getActive()))
                .filter(m -> m.getCreatedAt().isAfter(windowStart) && m.getCreatedAt().isBefore(windowEnd))
                .toList();

        for (Match match : approachingMatches) {
            User companyUser = match.getProject().getCompany().getUser();
            String actionUrl = "/matches/" + match.getId() + "/status-check";

            boolean alreadyNotified = notificationRepository.existsByUserIdAndTypeAndActionUrl(
                    companyUser.getId(), NotificationType.MATCH_STATUS_CHECK, actionUrl);

            if (!alreadyNotified) {
                notificationService.notifyMatchStatusCheck(
                        companyUser,
                        match.getProfessional().getName(),
                        match.getProject().getTitle(),
                        match.getId());
            }
        }
    }
}
