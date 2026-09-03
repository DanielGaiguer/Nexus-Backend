package com.main.nexus.repository;

import com.main.nexus.model.CommissionCharge;
import com.main.nexus.model.enums.CommissionChargeStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommissionChargeRepository extends JpaRepository<CommissionCharge, Long> {

    Optional<CommissionCharge> findByMatchConfirmationId(Long matchConfirmationId);

    Optional<CommissionCharge> findByMpPaymentId(String mpPaymentId);

    List<CommissionCharge> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<CommissionCharge> findByStatusOrderByCreatedAtAsc(CommissionChargeStatus status);

    List<CommissionCharge> findByStatusInOrderByCreatedAtDesc(List<CommissionChargeStatus> statuses);

    // A cobranca "que causou o bloqueio" de um contratante -- a mais recente
    // que ainda nao foi paga.
    Optional<CommissionCharge> findFirstByCompanyIdAndStatusInOrderByCreatedAtDesc(
            Long companyId, List<CommissionChargeStatus> statuses);

    boolean existsByCompanyIdAndStatusIn(Long companyId, List<CommissionChargeStatus> statuses);

    // LGPD -- exclusão de conta: só há obrigação legal de retenção do
    // CompanyFiscalProfile se existir cobrança/nota emitida referenciando a
    // empresa (o fato gerador fiscal). Sem nenhuma, o perfil fiscal é
    // anonimizado junto com o resto da Company.
    boolean existsByCompanyId(Long companyId);

    // Cobrancas travadas em PROCESSING ha muito tempo -- o job re-consulta o MP.
    @Query("SELECT c FROM CommissionCharge c "
         + "WHERE c.status = com.main.nexus.model.enums.CommissionChargeStatus.PROCESSING "
         + "AND c.mpPaymentId IS NOT NULL AND c.updatedAt < :threshold")
    List<CommissionCharge> findStaleProcessing(@Param("threshold") LocalDateTime threshold);

    // ─── Agregacoes do painel financeiro (Prompt 7) ─────────────────

    long countByStatus(CommissionChargeStatus status);

    long countByCompanyIdAndStatus(Long companyId, CommissionChargeStatus status);

    long countByCompanyIdAndStatusIn(Long companyId, List<CommissionChargeStatus> statuses);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CommissionCharge c WHERE c.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") CommissionChargeStatus status);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CommissionCharge c "
         + "WHERE c.company.id = :companyId AND c.status = :status")
    BigDecimal sumAmountByCompanyAndStatus(@Param("companyId") Long companyId,
                                           @Param("status") CommissionChargeStatus status);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CommissionCharge c "
         + "WHERE c.company.id = :companyId AND c.status IN :statuses")
    BigDecimal sumAmountByCompanyAndStatusIn(@Param("companyId") Long companyId,
                                             @Param("statuses") List<CommissionChargeStatus> statuses);

    // Receita paga por mes (para o grafico) -- [year, month, sum].
    @Query("SELECT YEAR(c.paidAt), MONTH(c.paidAt), COALESCE(SUM(c.amount), 0) "
         + "FROM CommissionCharge c "
         + "WHERE c.status = com.main.nexus.model.enums.CommissionChargeStatus.PAID "
         + "AND c.paidAt >= :since "
         + "GROUP BY YEAR(c.paidAt), MONTH(c.paidAt) "
         + "ORDER BY YEAR(c.paidAt) ASC, MONTH(c.paidAt) ASC")
    List<Object[]> findMonthlyPaidAmounts(@Param("since") LocalDateTime since);
}
