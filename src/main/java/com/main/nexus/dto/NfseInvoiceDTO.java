package com.main.nexus.dto;

import com.main.nexus.model.NfseInvoice;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Uma NFS-e para as telas (histórico do contratante + fila do Admin).
public record NfseInvoiceDTO(
        Long id,
        Long chargeId,
        Long matchId,
        Long companyId,
        String companyName,
        String projectTitle,
        String professionalName,
        BigDecimal amount,
        String status,
        String numero,
        String linkPdf,
        String linkXml,
        String failureReason,
        int attempts,
        LocalDateTime createdAt,
        LocalDateTime issuedAt
) {
    public static NfseInvoiceDTO from(NfseInvoice n) {
        if (n.getPortalSubscriptionCharge() != null) {
            var c = n.getPortalSubscriptionCharge();
            return new NfseInvoiceDTO(
                    n.getId(),
                    c.getId(),
                    null,
                    n.getCompany().getId(),
                    n.getCompany().getCompanyName(),
                    "Plataforma personalizada — " + c.getCustomPortal().getSubdomain(),
                    null,
                    c.getAmount(),
                    n.getStatus().name(),
                    n.getNumero(),
                    n.getLinkPdf(),
                    n.getLinkXml(),
                    n.getFailureReason(),
                    n.getAttempts(),
                    n.getCreatedAt(),
                    n.getIssuedAt());
        }
        var charge = n.getCommissionCharge();
        var match = charge.getMatchConfirmation().getMatch();
        return new NfseInvoiceDTO(
                n.getId(),
                charge.getId(),
                match.getId(),
                n.getCompany().getId(),
                n.getCompany().getCompanyName(),
                match.getProject().getTitle(),
                match.getProfessional().getName(),
                charge.getAmount(),
                n.getStatus().name(),
                n.getNumero(),
                n.getLinkPdf(),
                n.getLinkXml(),
                n.getFailureReason(),
                n.getAttempts(),
                n.getCreatedAt(),
                n.getIssuedAt());
    }
}
