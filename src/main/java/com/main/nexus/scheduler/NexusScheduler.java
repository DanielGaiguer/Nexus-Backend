package com.main.nexus.scheduler;

import com.main.nexus.service.MatchExpirationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Essa infraestrutura só funciona porque NexusApplication (Fluxo 1) tem @EnableScheduling na classe principal
@Component
public class NexusScheduler {

    @Autowired
    private MatchExpirationService matchExpirationService;

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
}
