package com.main.nexus.repository;

import com.main.nexus.model.NfseInvoice;
import com.main.nexus.model.enums.NfseInvoiceStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NfseInvoiceRepository extends JpaRepository<NfseInvoice, Long> {

    Optional<NfseInvoice> findByCommissionChargeId(Long commissionChargeId);

    Optional<NfseInvoice> findByPortalSubscriptionChargeId(Long portalSubscriptionChargeId);

    Optional<NfseInvoice> findByEnotasId(String enotasId);

    List<NfseInvoice> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<NfseInvoice> findByStatusOrderByCreatedAtDesc(NfseInvoiceStatus status);

    List<NfseInvoice> findAllByOrderByCreatedAtDesc();

    List<NfseInvoice> findByStatusOrderByCreatedAtAsc(NfseInvoiceStatus status);

    @Query("SELECT n FROM NfseInvoice n "
         + "WHERE n.status = com.main.nexus.model.enums.NfseInvoiceStatus.PROCESSING "
         + "AND n.enotasId IS NOT NULL AND n.updatedAt < :threshold")
    List<NfseInvoice> findStaleProcessing(@Param("threshold") LocalDateTime threshold);

    // ─── Agregacoes do painel financeiro (Prompt 7) ─────────────────

    long countByStatus(NfseInvoiceStatus status);

    long countByStatusIn(List<NfseInvoiceStatus> statuses);

    long countByCompanyIdAndStatus(Long companyId, NfseInvoiceStatus status);

    long countByCompanyIdAndStatusIn(Long companyId, List<NfseInvoiceStatus> statuses);
}
