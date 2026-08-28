package com.main.nexus.dto;

import com.main.nexus.model.CustomPortal;
import java.util.List;

// Recorte PÚBLICO de um CustomPortal — o que a página pública em
// empresa.nexus.com.br precisa. Sem nada de assinatura/pagamento/auditoria.
// status vai junto pra a página decidir entre renderizar (ACTIVE) ou mostrar
// "plataforma indisponível" (SUSPENDED/CANCELED).
public record PublicCustomPortalDTO(
        Long companyId,
        String companyName,
        String subdomain,
        String status,
        String displayName,
        String primaryColor,
        String logoUrl,
        String bannerUrl,
        String faviconUrl,
        String aboutText,
        List<CustomPortalSectionDTO> sections
) {
    public static PublicCustomPortalDTO from(CustomPortal p) {
        return new PublicCustomPortalDTO(
                p.getCompany().getId(),
                p.getCompany().getCompanyName(),
                p.getSubdomain(),
                p.getStatus().name(),
                p.getDisplayName(),
                p.getPrimaryColor(),
                p.getLogoUrl(),
                p.getBannerUrl(),
                p.getFaviconUrl(),
                p.getAboutText(),
                p.getSections().stream().map(CustomPortalSectionDTO::from).toList()
        );
    }
}
