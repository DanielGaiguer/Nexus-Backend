package com.main.nexus.dto;

import com.main.nexus.model.CustomPortal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Representacao de uma plataforma personalizada (CustomPortal) para as telas.
// Enums vao como String crua, mesmo padrao das outras DTOs. Inclui os campos de
// customizacao visual (Prompt 2) — todos podem vir nulos/vazios.
public record CustomPortalDTO(
        Long id,
        Long companyId,
        String companyName,
        String companyEmail,
        String status,
        String subdomain,
        String planName,
        BigDecimal planPrice,
        LocalDate subscriptionStartDate,
        LocalDate nextDueDate,
        String paymentStatus,
        boolean createdFromRequest,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        // ── branding ──
        String displayName,
        String primaryColor,
        String logoUrl,
        String bannerUrl,
        String faviconUrl,
        String aboutText,
        List<CustomPortalSectionDTO> sections,
        SocialLinksDTO socialLinks
) {
    public static CustomPortalDTO from(CustomPortal p) {
        return new CustomPortalDTO(
                p.getId(),
                p.getCompany().getId(),
                p.getCompany().getCompanyName(),
                p.getCompany().getUser() != null ? p.getCompany().getUser().getEmail() : null,
                p.getStatus().name(),
                p.getSubdomain(),
                p.getPlanName(),
                p.getPlanPrice(),
                p.getSubscriptionStartDate(),
                p.getNextDueDate(),
                p.getPaymentStatus().name(),
                p.getOriginRequest() != null,
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getDisplayName(),
                p.getPrimaryColor(),
                p.getLogoUrl(),
                p.getBannerUrl(),
                p.getFaviconUrl(),
                p.getAboutText(),
                p.getSections().stream().map(CustomPortalSectionDTO::from).toList(),
                SocialLinksDTO.from(p.getSocialLinks())
        );
    }
}
