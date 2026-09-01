package com.main.nexus.controller;

import com.main.nexus.dto.FiscalConfigDTO;
import com.main.nexus.dto.UpdateFiscalConfigDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.service.NfseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Configuração fiscal do Nexus para emissão de NFS-e, editável pelo Admin
// (Prompt 6). Protegido pela regra genérica /api/admin/** (hasRole("ADMIN")).
// A identidade fiscal (CNPJ, IM, regime, código de serviço, certificado) fica no
// painel do eNotas -- aqui só o id da empresa emitente e a descrição padrão.
@RestController
@RequestMapping("/api/admin/fiscal-config")
public class AdminFiscalConfigController {

    @Autowired
    private NfseService nfseService;

    @GetMapping
    public ResponseEntity<FiscalConfigDTO> get() {
        return ResponseEntity.ok(nfseService.getConfigDTO());
    }

    @PutMapping
    public ResponseEntity<FiscalConfigDTO> update(@RequestBody UpdateFiscalConfigDTO body) {
        return ResponseEntity.ok(nfseService.updateConfig(
                body.enotasEmpresaId(), body.defaultServiceDescription(), loggedUserId()));
    }

    private Long loggedUserId() {
        UserDTO logged = (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return logged.id();
    }
}
