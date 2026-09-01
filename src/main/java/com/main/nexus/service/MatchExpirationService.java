package com.main.nexus.service;

import com.main.nexus.model.Match;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.MatchRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MatchExpirationService {

    private static final int EXPIRATION_DAYS = 30;

    @Autowired
    private MatchRepository matchRepository;

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

    // Nota: o antigo aviso de "status check se aproximando" (14 dias, so para o
    // contratante) saiu no Prompt 2 da camada financeira. A pergunta pos-contratacao
    // agora acontece uma unica vez, aos 30 dias, para os DOIS lados, e e orquestrada
    // por MatchStatusCheckService (janela de confirmacao). Ver NexusScheduler.
}
