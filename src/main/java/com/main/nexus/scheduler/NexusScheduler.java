package com.main.nexus.scheduler;

import com.main.nexus.service.MatchExpirationService;
import com.main.nexus.service.NotificationService;
import com.main.nexus.service.ProfessionalInactivityService;
import com.main.nexus.service.ProposalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Essa infraestrutura só funciona porque NexusApplication (Fluxo 1) tem @EnableScheduling na classe principal
@Component
public class NexusScheduler {

    @Autowired
    private MatchExpirationService matchExpirationService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ProfessionalInactivityService professionalInactivityService;

    @Autowired
    private ProposalService proposalService;

    // segundo minuto hora dia-do-mês mês dia-da-semana
    @Scheduled(cron = "0 0 0 * * *") //meia-noite, todo dia. Roda checkAndExpireMatches (a checagem de expiração de 30 dias)   
    public void runMatchExpirationCheck() { // Nao precisa de atencao imediata da empresa, por isso a escolha do horario
        matchExpirationService.checkAndExpireMatches();
    }

    // E avisado 8 horas pois e o horario de inicio do expediente, maximizando a chance de resposta no mesmo dia
    @Scheduled(cron = "0 0 8 * * *")// 8h da manhã, todo dia. Roda checkAndNotifyApproaching (o aviso de status check de ~15 dias)
    public void runMatchApproachingCheck() {
        matchExpirationService.checkAndNotifyApproaching();
    }

    // 3h da manhã, todo dia — horário de baixo tráfego. Remove notificações lidas com
    // mais de 30 dias, de todos os usuários. Antes disso, nada disparava essa limpeza:
    // o endpoint existia (DELETE /api/notifications/old) mas nenhum lugar do frontend
    // chamava, então tb_notification só crescia.
    @Scheduled(cron = "0 0 3 * * *")
    public void runOldNotificationsCleanup() {
        notificationService.deleteAllOldNotifications();
    }

    // 4h da manhã, todo dia — horário de baixo tráfego. Marca como indisponível quem
    // não loga há 1 mês, pra parar de gerar/pontuar match pra quem sumiu da plataforma.
    @Scheduled(cron = "0 0 4 * * *")
    public void runProfessionalInactivityCheck() {
        professionalInactivityService.markInactiveProfessionalsUnavailable();
    }

    // 5h da manhã, todo dia — horário de baixo tráfego, mesmo espírito dos jobs acima. Marca
    // como EXPIRED as propostas PENDING cuja validade (definida pelo próprio profissional no
    // envio) já passou, sem exigir que a empresa tenha respondido.
    @Scheduled(cron = "0 0 5 * * *")
    public void runProposalExpirationCheck() {
        proposalService.expirePendingProposals();
    }
}
