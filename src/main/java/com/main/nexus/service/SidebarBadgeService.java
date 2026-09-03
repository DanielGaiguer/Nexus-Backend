package com.main.nexus.service;

import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.CommissionChargeStatus;
import com.main.nexus.model.enums.CompanyStatus;
import com.main.nexus.model.enums.CustomPortalRequestStatus;
import com.main.nexus.model.enums.MatchConfirmationStatus;
import com.main.nexus.model.enums.NfseInvoiceStatus;
import com.main.nexus.model.enums.NotificationType;
import com.main.nexus.model.enums.ScreeningInvitationStatus;
import com.main.nexus.model.enums.SidebarSection;
import com.main.nexus.repository.CommissionChargeRepository;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.CustomPortalRequestRepository;
import com.main.nexus.repository.MatchConfirmationRepository;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.NfseInvoiceRepository;
import com.main.nexus.repository.NotificationRepository;
import com.main.nexus.repository.ProposalRepository;
import com.main.nexus.repository.ScreeningInvitationRepository;
import com.main.nexus.repository.SectionViewRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Contadores dos badges da sidebar, recalculados a cada carga da sidebar (sem
// tempo real). Reaproveita a APARÊNCIA do badge de "Conversas"; a lógica de
// dados varia por seção:
//
//  • Padrão A -- "pendência por status": conta registros que já existem num
//    status que indica pendência (match aguardando resposta, proposta PENDING,
//    cobrança FAILED, ...). Zera sozinho quando o item é resolvido. Sem nenhuma
//    tabela nova.
//
//  • Padrão B -- "houve uma atualização que você ainda não viu" (sem status de
//    pendência natural): conta as notificações daquela seção criadas depois do
//    último "visto" do usuário (SectionView). Abrir/sair da seção empurra o "visto".
//
// "Conversas" e "Suporte" já têm badge próprio (contagem de mensagens não lidas)
// e não passam por aqui.
@Service
public class SidebarBadgeService {

    // Padrão B -- tipos de notificação que alimentam cada seção.
    private static final List<NotificationType> PRO_MATCHES_EVENTS = List.of(
            NotificationType.MATCH_CONFIRMED,
            NotificationType.INVITE_REJECTED);

    private static final List<NotificationType> PRO_PROPOSALS_EVENTS = List.of(
            NotificationType.PROPOSAL_ACCEPTED,
            NotificationType.PROPOSAL_REJECTED,
            NotificationType.PROPOSAL_POSITION_FILLED,
            NotificationType.PROPOSAL_EXPIRED);

    private static final List<NotificationType> COMPANY_PORTAL_EVENTS = List.of(
            NotificationType.CUSTOM_PORTAL_REQUEST_APPROVED,
            NotificationType.CUSTOM_PORTAL_REQUEST_REJECTED,
            NotificationType.CUSTOM_PORTAL_RENEWAL_DUE,
            NotificationType.CUSTOM_PORTAL_SUSPENDED,
            NotificationType.PORTAL_SUBSCRIPTION_PAYMENT_FAILED);

    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    @Autowired private ProfessionalService professionalService;
    @Autowired private CompanyService companyService;
    @Autowired private MatchService matchService;

    @Autowired private SectionViewRepository sectionViewRepository;
    @Autowired private NotificationRepository notificationRepository;

    @Autowired private MatchRepository matchRepository;
    @Autowired private ScreeningInvitationRepository screeningInvitationRepository;
    @Autowired private ProposalRepository proposalRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private MatchConfirmationRepository matchConfirmationRepository;
    @Autowired private CommissionChargeRepository commissionChargeRepository;
    @Autowired private NfseInvoiceRepository nfseInvoiceRepository;
    @Autowired private CustomPortalRequestRepository customPortalRequestRepository;

    // href do item da sidebar -> contagem. Só entram os itens com contagem > 0.
    // readOnly=true: são ~9-13 contagens por chamada (a cada montagem da sidebar
    // + a cada poll); sem isto cada uma abria a sua própria transação autocommit.
    @Transactional(readOnly = true)
    public Map<String, Long> badgesFor(UserDTO logged) {
        Map<String, Long> badges = new LinkedHashMap<>();
        String role = logged.role();
        if (role != null) {
            switch (role) {
                case "PROFESSIONAL" -> professionalBadges(logged, badges);
                case "COMPANY" -> companyBadges(logged, badges);
                case "ADMIN" -> adminBadges(badges);
                default -> { /* sem badges */ }
            }
        }
        badges.values().removeIf(v -> v == null || v <= 0);
        return badges;
    }

    private void professionalBadges(UserDTO logged, Map<String, Long> badges) {
        Long professionalId = professionalService.findByUserId(logged.id())
                .map(p -> p.getId()).orElse(null);
        if (professionalId == null) {
            return;
        }

        // Dashboard: (A) janela ainda aberta que VOCÊ não respondeu -- confirmação
        // de 30 dias e/ou avaliação de match encerrado. Mesmos critérios dos
        // diálogos que já abrem no dashboard (usePendingStatusCheck / usePendingReview).
        badges.put("/pro/dashboard",
                matchConfirmationRepository.countPendingForParty(null, professionalId, AuthorType.PROFESSIONAL)
                        + matchRepository.countPendingReviewsForProfessional(professionalId));

        // Matches: (A) convites recebidos aguardando sua resposta
        //        + (B) interesse que você enviou e o outro lado já respondeu.
        long matchesA = matchService.countPendingInvitesForProfessional(professionalId);
        long matchesB = countUnseen(logged.id(), SidebarSection.PRO_MATCHES, PRO_MATCHES_EVENTS);
        badges.put("/pro/matches", matchesA + matchesB);

        // Propostas: (B) proposta enviada que mudou de status.
        badges.put("/pro/proposals",
                countUnseen(logged.id(), SidebarSection.PRO_PROPOSALS, PRO_PROPOSALS_EVENTS));

        // Processos Seletivos: (A) convite de teste ainda não respondido.
        badges.put("/pro/screening-invitations",
                screeningInvitationRepository.countByProfessionalIdAndStatusIn(
                        professionalId,
                        List.of(ScreeningInvitationStatus.SENT,
                                ScreeningInvitationStatus.IN_PROGRESS)));
    }

    private void companyBadges(UserDTO logged, Map<String, Long> badges) {
        Long companyId = companyService.findByUserId(logged.id())
                .map(c -> c.getId()).orElse(null);
        if (companyId == null) {
            return;
        }

        // Janela de confirmação de 30 dias em que a EMPRESA ainda não respondeu --
        // mesmo critério do selo "Confirmação pendente — responda" nos cards de match.
        long confirmationsAwaiting = matchConfirmationRepository.countPendingForParty(
                companyId, null, AuthorType.COMPANY);

        // Dashboard: (A) janela ainda aberta que VOCÊ não respondeu -- confirmação
        // de 30 dias e/ou avaliação de match encerrado.
        badges.put("/company/dashboard",
                confirmationsAwaiting
                        + matchRepository.countPendingReviewsForCompany(companyId));

        // Matches: (A) interesses recebidos aguardando sua resposta
        //        + (A) janela de confirmação de 30 dias aguardando sua resposta.
        badges.put("/company/matches",
                matchService.countReceivedInterestsForCompany(companyId)
                        + confirmationsAwaiting);

        // Propostas: (A) proposta recebida ainda PENDING cujo autor está numa etapa
        // de triagem ativa (aba "Em processo" da tela).
        badges.put("/company/proposals",
                proposalRepository.countInScreeningPendingProposalsForCompany(companyId));

        // Processos Seletivos: (A) teste respondido aguardando sua avaliação.
        badges.put("/company/screening-invitations",
                screeningInvitationRepository
                        .countByScreeningStageScreeningQuestionnaireProjectCompanyIdAndStatus(
                                companyId, ScreeningInvitationStatus.SUBMITTED));

        // Financeiro: (A) cobrança de comissão pendente/falhou.
        badges.put("/company/billing",
                commissionChargeRepository.countByCompanyIdAndStatusIn(
                        companyId,
                        List.of(CommissionChargeStatus.PENDING, CommissionChargeStatus.FAILED)));

        // Minha Plataforma: (B) solicitação de portal decidida / aviso de assinatura.
        badges.put("/company/custom-portal",
                countUnseen(logged.id(), SidebarSection.COMPANY_CUSTOM_PORTAL, COMPANY_PORTAL_EVENTS));
    }

    private void adminBadges(Map<String, Long> badges) {
        // Todos Padrão A -- contagem de registros num status que pede ação do Admin.
        badges.put("/admin/approvals",
                companyRepository.countByStatus(CompanyStatus.PENDING));

        badges.put("/admin/custom-portals",
                customPortalRequestRepository.countByStatus(CustomPortalRequestStatus.PENDING));

        badges.put("/admin/confirmations",
                matchConfirmationRepository.countByStatus(
                        MatchConfirmationStatus.PENDING_ADMIN_REVIEW));

        badges.put("/admin/commission-charges",
                commissionChargeRepository.countByStatus(CommissionChargeStatus.FAILED));

        badges.put("/admin/invoices",
                nfseInvoiceRepository.countByStatus(NfseInvoiceStatus.FAILED));
    }

    // Padrão B: eventos daquela seção mais novos que o último "visto" do usuário.
    // Uma query só -- o seenAt (ou EPOCH, se ainda não houver SectionView) é
    // resolvido por subquery em countUnseenForSection.
    private long countUnseen(Long userId, SidebarSection section, List<NotificationType> types) {
        return notificationRepository.countUnseenForSection(userId, types, section, EPOCH);
    }

    // Chamado quando o usuário abre/sai de uma seção do Padrão B -- zera o badge.
    // Upsert atômico: o frontend pode disparar isto mais de uma vez quase ao mesmo
    // tempo (React Strict Mode remonta o efeito no dev; navegação rápida), e sem o
    // ON DUPLICATE KEY dois INSERT concorrentes estouram a unique (user_id, section).
    @Transactional
    public void markSeen(Long userId, SidebarSection section) {
        sectionViewRepository.upsertSeenAt(userId, section.name(), LocalDateTime.now());
    }
}
