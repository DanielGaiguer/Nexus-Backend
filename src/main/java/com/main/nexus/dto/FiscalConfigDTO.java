package com.main.nexus.dto;

import com.main.nexus.model.FiscalConfig;
import java.time.LocalDateTime;

// Configuracao fiscal do Nexus para a tela do Admin. `nfseEnabled` / `simulated`
// sao derivados (api-key + empresaId presentes, ou modo simulate).
public record FiscalConfigDTO(
        String enotasEmpresaId,
        String defaultServiceDescription,
        boolean nfseEnabled,
        boolean simulated,
        LocalDateTime updatedAt,
        String updatedByAdminEmail
) {
    public static FiscalConfigDTO from(FiscalConfig c, boolean nfseEnabled, boolean simulated) {
        return new FiscalConfigDTO(
                c.getEnotasEmpresaId(),
                c.getDefaultServiceDescription(),
                nfseEnabled,
                simulated,
                c.getUpdatedAt(),
                c.getUpdatedByAdmin() != null ? c.getUpdatedByAdmin().getEmail() : null);
    }
}
