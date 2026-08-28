package com.main.nexus.dto;

import java.util.List;

// Corpo de PUT .../custom-portal/branding — salva os campos de texto da
// customizacao visual de uma vez (imagens tem endpoint proprio de upload).
// sections substitui a lista inteira; a ordem enviada e a ordem final.
public record UpdateCustomPortalBrandingDTO(
        String displayName,
        String primaryColor,
        String aboutText,
        List<CustomPortalSectionDTO> sections,
        SocialLinksDTO socialLinks
) {}
