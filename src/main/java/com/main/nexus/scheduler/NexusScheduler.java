package com.main.nexus.scheduler;

import com.main.nexus.service.BillingService;
import com.main.nexus.service.CustomPortalService;
import com.main.nexus.service.MatchExpirationService;
import com.main.nexus.service.MatchStatusCheckService;
import com.main.nexus.service.NfseService;
import com.main.nexus.service.NotificationService;
import com.main.nexus.service.PortalSubscriptionService;
import com.main.nexus.service.ProfessionalInactivityService;
import com.main.nexus.service.ProposalService;
import com.main.nexus.service.ScreeningInvitationService;
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

    @Autowired
    private ScreeningInvitationService screeningInvitationService;

    @Autowired
    private CustomPortalService customPortalService;

    @Autowired
    private MatchStatusCheckService matchStatusCheckService;

    @Autowired
    private BillingService billingService;

    @Autowired
    private NfseService nfseService;

    @Autowired
    private PortalSubscriptionService portalSubscriptionService;

    // segundo minuto hora dia-do-mês mês dia-da-semana
    @Scheduled(cron = "0 0 0 * * *") //meia-noite, todo dia. Roda checkAndExpireMatches (a checagem de expiração de 30 dias)   
    public void runMatchExpirationCheck() { // Nao precisa de atencao imediata da empresa, por isso a escolha do horario
        matchExpirationService.checkAndExpireMatches();
    }

    // 8h da manhã, todo dia — início do expediente, maximiza a chance de resposta no mesmo dia.
    // Abre a janela de confirmação pós-contratação para matches cujo fechamento completou 30 dias
    // (camada financeira, Prompt 2). Substituiu o antigo aviso de status check de ~15 dias.
    @Scheduled(cron = "0 0 8 * * *")
    public void runConfirmationWindowOpen() {
        matchStatusCheckService.openDueConfirmationWindows();
    }

    // 8h30, todo dia. Move para revisão do Admin as janelas de confirmação cujo prazo de 7 dias
    // estourou sem os dois lados responderem (PENDING_ADMIN_REVIEW / NO_RESPONSE).
    @Scheduled(cron = "0 30 8 * * *")
    public void runConfirmationWindowExpiry() {
        matchStatusCheckService.closeOverdueConfirmationWindows();
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

    // 6h da manhã, todo dia — mesmo espírito dos jobs acima. Marca como EXPIRED os
    // ScreeningInvitation SENT/IN_PROGRESS cujo prazo de resposta (definido pelo
    // ScreeningQuestionnaire, contado a partir do envio) já passou sem submissão.
    @Scheduled(cron = "0 0 6 * * *")
    public void runScreeningInvitationExpirationCheck() {
        screeningInvitationService.expirePendingInvitations();
    }

    // 9h da manhã, todo dia — início do expediente, mesmo raciocínio do aviso de
    // status check de match (maximiza a chance de o contratante regularizar a
    // assinatura no mesmo dia). Avisa quem tem plataforma personalizada ACTIVE
    // com vencimento em até 7 dias; a trava lastRenewalReminderFor evita repetir.
    @Scheduled(cron = "0 0 9 * * *")
    public void runCustomPortalRenewalCheck() {
        customPortalService.notifyUpcomingRenewals();
    }

    // 9h05, todo dia. Cobrança automática da assinatura da plataforma personalizada:
    // no modo simulado gera a mensalidade do ciclo vencido (o Admin decide o
    // resultado em /admin/portal-subscription-charges); suspende os portais cuja
    // carência de 7 dias após uma falha já passou; e, no modo real, re-consulta o
    // Mercado Pago sobre cobranças presas em PROCESSING.
    @Scheduled(cron = "0 5 9 * * *")
    public void runPortalSubscriptionBilling() {
        portalSubscriptionService.runBillingCycle();
    }

    // A cada 20 min — rede de segurança da cobrança de comissão (Prompt 5): tenta de
    // novo as cobranças PENDING de quem já tem cartão e re-consulta o Mercado Pago
    // sobre as que ficaram PROCESSING (caso o webhook não tenha chegado).
    @Scheduled(cron = "0 */20 * * * *")
    public void runCommissionChargeSweep() {
        billingService.processStuckCharges();
    }

    // A cada 30 min: re-tenta as NFS-e PENDING e re-consulta o eNotas sobre as
    // que ficaram PROCESSING (caso o webhook não tenha chegado). Rede de
    // segurança da emissão automática de nota por comissão paga (Prompt 6).
    @Scheduled(cron = "0 */30 * * * *")
    public void runNfseSweep() {
        nfseService.processStuckInvoices();
    }
}
