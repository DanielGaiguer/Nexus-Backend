package com.main.nexus.model.enums;

// Paleta fixa de cores para os badges de certificados/eventos do profissional.
// Os hex ficam documentados aqui só como referência — quem realmente pinta o
// badge é o CSS do frontend (classes .nexus-credential-*), mapeadas 1:1 pelo nome.
public enum BadgeColor {
    NEXUS,      // #6b6eff — --primary
    SLATE,      // #475569 — --text-muted
    CIANO,      // #22d3ee — --info
    VIOLETA,    // #a78bfa — análoga ao primário
    TEAL,       // #2dd4bf
    AMBAR,      // #fbbf24
    ROSA,       // #fb7185
    ESMERALDA   // #34d399
}
