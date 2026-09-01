package com.main.nexus.repository;

import com.main.nexus.model.MatchConfirmation;
import com.main.nexus.model.enums.MatchConfirmationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchConfirmationRepository extends JpaRepository<MatchConfirmation, Long> {

    Optional<MatchConfirmation> findByMatchId(Long matchId);

    boolean existsByMatchId(Long matchId);

    List<MatchConfirmation> findByStatus(MatchConfirmationStatus status);

    long countByStatus(MatchConfirmationStatus status);

    // Janelas de confirmacao de uma empresa num dado estado -- alimenta o
    // "aguardando confirmacao de 30 dias" do painel financeiro (Prompt 7).
    List<MatchConfirmation> findByMatchProjectCompanyIdAndStatusOrderByOpenedAtAsc(
            Long companyId, MatchConfirmationStatus status);

    // Job de expiracao: janelas ainda abertas cujo prazo ja passou.
    List<MatchConfirmation> findByStatusAndDeadlineBefore(
            MatchConfirmationStatus status, LocalDateTime instant);

    // Janela aberta mais antiga de um match do qual o usuario e parte e que
    // aquele lado ainda nao respondeu -- alimenta o indicador "confirmacao
    // pendente" no dashboard. `answeredBy` e o AuthorType do lado (COMPANY/PROFESSIONAL).
    @Query("""
            SELECT c FROM MatchConfirmation c
            WHERE c.status = com.main.nexus.model.enums.MatchConfirmationStatus.AWAITING_RESPONSES
              AND (:companyId IS NULL OR c.match.project.company.id = :companyId)
              AND (:professionalId IS NULL OR c.match.professional.id = :professionalId)
              AND NOT EXISTS (
                  SELECT 1 FROM MatchStatusCheck s
                  WHERE s.match = c.match AND s.answeredBy = :answeredBy)
            ORDER BY c.openedAt ASC
            """)
    List<MatchConfirmation> findPendingForParty(
            @Param("companyId") Long companyId,
            @Param("professionalId") Long professionalId,
            @Param("answeredBy") com.main.nexus.model.enums.AuthorType answeredBy);

    // Painel do Admin -- lista filtravel por status e/ou empresa.
    @Query("""
            SELECT c FROM MatchConfirmation c
            WHERE (:status IS NULL OR c.status = :status)
              AND (:companyId IS NULL OR c.match.project.company.id = :companyId)
            ORDER BY c.openedAt DESC
            """)
    List<MatchConfirmation> findForAdmin(
            @Param("status") MatchConfirmationStatus status,
            @Param("companyId") Long companyId);

    List<MatchConfirmation> findByMatchProjectCompanyIdOrderByOpenedAtDesc(Long companyId);

    // Contagens por status de todas as confirmacoes de uma empresa -- [status, count].
    @Query("""
            SELECT c.status, COUNT(c) FROM MatchConfirmation c
            WHERE c.match.project.company.id = :companyId
            GROUP BY c.status
            """)
    List<Object[]> countByStatusForCompany(@Param("companyId") Long companyId);

    // Contagens por motivo de pendencia de uma empresa -- [pendingReason, count].
    @Query("""
            SELECT c.pendingReason, COUNT(c) FROM MatchConfirmation c
            WHERE c.match.project.company.id = :companyId AND c.pendingReason IS NOT NULL
            GROUP BY c.pendingReason
            """)
    List<Object[]> countByPendingReasonForCompany(@Param("companyId") Long companyId);

    // Empresas que tem ao menos uma confirmacao -- base da fila do Admin.
    @Query("SELECT DISTINCT c.match.project.company.id FROM MatchConfirmation c")
    List<Long> findCompanyIdsWithConfirmations();
}
