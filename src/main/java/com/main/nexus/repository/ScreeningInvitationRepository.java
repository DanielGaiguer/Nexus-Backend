package com.main.nexus.repository;

import com.main.nexus.model.ScreeningInvitation;
import com.main.nexus.model.enums.ScreeningInvitationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScreeningInvitationRepository extends JpaRepository<ScreeningInvitation, Long> {
    // Tentativas de um profissional numa vaga (via etapa -> questionário -> projeto), em
    // qualquer etapa, mais recente primeiro -- usado pra montar
    // MatchResponseDTO/CandidateComparisonItemDTO/ProposalResponseDTO.screeningInvitations.
    List<ScreeningInvitation> findByScreeningStageScreeningQuestionnaireProjectIdAndProfessionalId(
            Long projectId, Long professionalId);

    // A tentativa mais recente pra (etapa, profissional) -- usada pelo gate pra decidir se
    // reaproveita uma em andamento ou abre uma nova, sempre escopado à etapa atual.
    Optional<ScreeningInvitation> findFirstByScreeningStageIdAndProfessionalIdOrderBySentAtDesc(
            Long screeningStageId, Long professionalId);

    // Esse par (etapa, profissional) já foi aprovado alguma vez? -- é como o gate decide se essa
    // etapa está "concluída" e pode avançar pra próxima.
    boolean existsByScreeningStageIdAndProfessionalIdAndStatus(
            Long screeningStageId, Long professionalId, ScreeningInvitationStatus status);

    // Usado por ScreeningQuestionnaireService pra decidir se uma etapa/questão já respondida
    // precisa virar `active=false` em vez de ser removida de verdade.
    boolean existsByScreeningStageId(Long screeningStageId);

    // Esse profissional específico já teve alguma tentativa nesta etapa (em qualquer status)? --
    // usado por findCurrentStage pra não pular uma etapa desativada (active=false) que ele já
    // tinha começado antes dela ser removida (sem efeito retroativo em quem já estava nela).
    boolean existsByScreeningStageIdAndProfessionalId(Long screeningStageId, Long professionalId);

    // Todas as tentativas pendentes de um questionário, em qualquer etapa, independente do
    // profissional -- usado quando o projeto inteiro fecha (ver
    // ScreeningInvitationService.cancelAllPendingForProject).
    List<ScreeningInvitation> findByScreeningStageScreeningQuestionnaireIdAndStatusIn(
            Long screeningQuestionnaireId, List<ScreeningInvitationStatus> statuses);

    List<ScreeningInvitation> findByStatusInAndDeadlineAtBefore(
            List<ScreeningInvitationStatus> statuses, LocalDateTime deadlineAtBefore);

    // Todas as tentativas de um profissional, em qualquer vaga/etapa -- base da tela "Processos
    // Seletivos" dele (ver ScreeningInvitationService.getProcessesForProfessional).
    List<ScreeningInvitation> findByProfessionalId(Long professionalId);

    // Todas as tentativas recebidas por uma empresa, em qualquer vaga/etapa/profissional -- base
    // da tela "Processos Seletivos" dela (ver ScreeningInvitationService.getProcessesForCompany).
    List<ScreeningInvitation> findByScreeningStageScreeningQuestionnaireProjectCompanyId(Long companyId);

    // Convites que ainda referenciam um destes matches como a ação pendente (pendingMatch) --
    // usado por MatchService pra separar, dentre os matches WAITING/COMPANY_INTERESTED, quais
    // têm mesmo um processo seletivo em andamento por trás (aba "Em processo").
    List<ScreeningInvitation> findByPendingMatchIdIn(List<Long> matchIds);

    // ─── Badges de sidebar (Padrão A) ──────────────────────────────────
    // Profissional: convites de teste técnico ainda não respondidos (SENT/IN_PROGRESS).
    long countByProfessionalIdAndStatusIn(
            Long professionalId, java.util.Collection<ScreeningInvitationStatus> statuses);

    // Contratante: testes já respondidos aguardando a avaliação da empresa (SUBMITTED).
    long countByScreeningStageScreeningQuestionnaireProjectCompanyIdAndStatus(
            Long companyId, ScreeningInvitationStatus status);
}
