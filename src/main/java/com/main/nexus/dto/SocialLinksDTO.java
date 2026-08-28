package com.main.nexus.dto;

import com.main.nexus.model.CustomPortalSocialLinks;

// Links de redes sociais da plataforma personalizada. Serve de leitura (dentro
// de CustomPortalDTO / PublicCustomPortalDTO) e de entrada (dentro de
// UpdateCustomPortalBrandingDTO).
public record SocialLinksDTO(
        String website,
        String linkedin,
        String instagram,
        String facebook,
        String youtube,
        String x,
        String github
) {
    public static SocialLinksDTO from(CustomPortalSocialLinks s) {
        if (s == null) {
            return new SocialLinksDTO(null, null, null, null, null, null, null);
        }
        return new SocialLinksDTO(
                s.getWebsite(), s.getLinkedin(), s.getInstagram(),
                s.getFacebook(), s.getYoutube(), s.getX(), s.getGithub());
    }
}
