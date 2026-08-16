package com.main.nexus.service;

import com.main.nexus.model.Professional;
import com.main.nexus.repository.ProfessionalRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// Marca como indisponível quem não acessa a plataforma há 1 mês — profissional
// indisponível não gera match novo nem participa do cálculo de score (ver
// MatchService.getScore/generateRankingForProject/getOpportunitiesForProfessional),
// então esse job existe pra desativar automaticamente quem sumiu, sem precisar de ação
// manual. Chamado por NexusScheduler, uma vez por dia.
@Service
public class ProfessionalInactivityService {

    private static final int INACTIVITY_DAYS = 30;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EmailService emailService;

    @Transactional
    public void markInactiveProfessionalsUnavailable() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(INACTIVITY_DAYS);

        List<Professional> inactive = professionalRepository
                .findInactiveAvailableProfessionals(threshold);

        for (Professional professional : inactive) {
            professional.setAvailable(false);

            notificationService.notifyAccountMarkedUnavailable(professional.getUser());

            emailService.send(
                professional.getUser().getEmail(),
                "Sua conta foi marcada como indisponível — Nexus",
                "Olá " + professional.getName() + ",\n\n" +
                "Faz um mês que você não entra na plataforma, então marcamos sua conta como " +
                "indisponível. Enquanto estiver assim, você não participa do cálculo de " +
                "compatibilidade com as oportunidades e não aparece nos rankings das empresas.\n\n" +
                "Para voltar a participar, acesse o Nexus, vá em \"Meu Perfil\" e marque-se como " +
                "disponível novamente.\n\nEquipe Nexus"
            );
        }

        professionalRepository.saveAll(inactive);
    }
}
