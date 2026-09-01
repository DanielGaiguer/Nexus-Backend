package com.main.nexus.controller;

import com.main.nexus.dto.NfseInvoiceDTO;
import com.main.nexus.dto.NfseModeDTO;
import com.main.nexus.dto.SimulateNfseRequestDTO;
import com.main.nexus.service.NfseService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Fila de NFS-e para o Admin (Prompt 6): acompanha as emissões e resolve as que
// falharam (dados fiscais inválidos etc.). /api/admin/** = hasRole("ADMIN").
@RestController
@RequestMapping("/api/admin/invoices")
public class AdminNfseController {

    @Autowired
    private NfseService nfseService;

    @GetMapping
    public ResponseEntity<List<NfseInvoiceDTO>> list(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(nfseService.listForAdmin(status));
    }

    // Diz ao painel se a simulação de emissão está disponível (modo simulate, sem eNotas).
    @GetMapping("/mode")
    public ResponseEntity<NfseModeDTO> mode() {
        return ResponseEntity.ok(nfseService.mode());
    }

    // Re-tenta a emissão de uma nota FAILED/PENDING (ex.: depois de o contratante
    // corrigir os dados fiscais).
    @PostMapping("/{id}/retry")
    public ResponseEntity<NfseInvoiceDTO> retry(@PathVariable Long id) {
        return ResponseEntity.ok(nfseService.retry(id));
    }

    // Modo simulate: o Admin decide o resultado da emissão. outcome = authorized | denied.
    @PostMapping("/{id}/simulate")
    public ResponseEntity<NfseInvoiceDTO> simulate(
            @PathVariable Long id,
            @RequestBody SimulateNfseRequestDTO body) {
        return ResponseEntity.ok(nfseService.simulateOutcome(id, body.outcome()));
    }
}
