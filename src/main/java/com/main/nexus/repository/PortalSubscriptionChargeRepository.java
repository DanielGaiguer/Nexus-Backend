package com.main.nexus.repository;

import com.main.nexus.model.PortalSubscriptionCharge;
import com.main.nexus.model.enums.PortalSubscriptionChargeStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PortalSubscriptionChargeRepository
        extends JpaRepository<PortalSubscriptionCharge, Long> {

    Optional<PortalSubscriptionCharge> findByMpPaymentId(String mpPaymentId);

    List<PortalSubscriptionCharge> findByCustomPortalIdOrderByCreatedAtDesc(Long customPortalId);

    List<PortalSubscriptionCharge> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<PortalSubscriptionCharge> findByStatusOrderByCreatedAtAsc(PortalSubscriptionChargeStatus status);

    List<PortalSubscriptionCharge> findByStatusInOrderByCreatedAtDesc(
            List<PortalSubscriptionChargeStatus> statuses);

    boolean existsByCustomPortalIdAndDueDateAndStatusIn(
            Long customPortalId, java.time.LocalDate dueDate,
            List<PortalSubscriptionChargeStatus> statuses);

    Optional<PortalSubscriptionCharge> findFirstByCustomPortalIdAndStatusInOrderByCreatedAtDesc(
            Long customPortalId, List<PortalSubscriptionChargeStatus> statuses);

    List<PortalSubscriptionCharge> findByCustomPortalIdAndStatusIn(
            Long customPortalId, List<PortalSubscriptionChargeStatus> statuses);

    @Query("SELECT c FROM PortalSubscriptionCharge c "
         + "WHERE c.status = com.main.nexus.model.enums.PortalSubscriptionChargeStatus.PROCESSING "
         + "AND c.mpPaymentId IS NOT NULL AND c.updatedAt < :threshold")
    List<PortalSubscriptionCharge> findStaleProcessing(@Param("threshold") LocalDateTime threshold);

    // ─── Agregacoes do painel financeiro (Prompt 7 / follow-up) ─────

    long countByStatus(PortalSubscriptionChargeStatus status);

    long countByStatusIn(List<PortalSubscriptionChargeStatus> statuses);

    long countByCompanyIdAndStatus(Long companyId, PortalSubscriptionChargeStatus status);

    long countByCompanyIdAndStatusIn(Long companyId, List<PortalSubscriptionChargeStatus> statuses);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM PortalSubscriptionCharge c WHERE c.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") PortalSubscriptionChargeStatus status);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM PortalSubscriptionCharge c WHERE c.status IN :statuses")
    BigDecimal sumAmountByStatusIn(@Param("statuses") List<PortalSubscriptionChargeStatus> statuses);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM PortalSubscriptionCharge c "
         + "WHERE c.company.id = :companyId AND c.status = :status")
    BigDecimal sumAmountByCompanyAndStatus(@Param("companyId") Long companyId,
                                           @Param("status") PortalSubscriptionChargeStatus status);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM PortalSubscriptionCharge c "
         + "WHERE c.company.id = :companyId AND c.status IN :statuses")
    BigDecimal sumAmountByCompanyAndStatusIn(@Param("companyId") Long companyId,
                                             @Param("statuses") List<PortalSubscriptionChargeStatus> statuses);
}
