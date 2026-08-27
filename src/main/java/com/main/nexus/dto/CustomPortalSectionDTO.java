package com.main.nexus.dto;

import com.main.nexus.model.CustomPortalSection;

// Uma seção institucional extra da plataforma personalizada. Serve tanto de
// leitura (dentro de CustomPortalDTO) quanto de entrada (dentro de
// UpdateCustomPortalBrandingDTO) — a ordem é a posição na lista.
public record CustomPortalSectionDTO(String title, String content) {
    public static CustomPortalSectionDTO from(CustomPortalSection s) {
        return new CustomPortalSectionDTO(s.getTitle(), s.getContent());
    }
}
