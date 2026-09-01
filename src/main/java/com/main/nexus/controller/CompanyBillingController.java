package com.main.nexus.controller;

import com.main.nexus.dto.BillingConfigDTO;
import com.main.nexus.dto.BillingStatusDTO;
import com.main.nexus.dto.CommissionChargeDTO;
import com.main.nexus.dto.CompanyFiscalProfileDTO;
import com.main.nexus.dto.NfseInvoiceDTO;
import com.main.nexus.dto.SaveCardRequestDTO;
import com.main.nexus.dto.UpdateCompanyFiscalProfileDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.service.BillingService;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.NfseService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Pagamento do contratante (camada financeira, Prompt 5). /api/company/** já é
// hasRole("COMPANY"). O cartão é tokenizado no frontend -- aqui só chega o token.
@RestController
@RequestMapping("/api/company/billing")
public class CompanyBillingController {

    @Autowired
    private BillingService billingService;

    @Autowired
    private NfseService nfseService;

    @Autowired
    private CompanyService companyService;

    // Config para o frontend inicializar o SDK do MP (sem NEXT_PUBLIC_*).
    @GetMapping("/config")
    public ResponseEntity<BillingConfigDTO> config() {
        return ResponseEntity.ok(billingService.getConfig());
    }

    @GetMapping("/status")
    public ResponseEntity<BillingStatusDTO> status() {
        return ResponseEntity.ok(billingService.getStatus(loggedCompany()));
    }

    @PostMapping("/card")
    public ResponseEntity<BillingStatusDTO> saveCard(@RequestBody SaveCardRequestDTO body) {
        return ResponseEntity.ok(billingService.saveCard(loggedCompany(), body.cardToken()));
    }

    @DeleteMapping("/card")
    public ResponseEntity<BillingStatusDTO> removeCard() {
        return ResponseEntity.ok(billingService.removeCard(loggedCompany()));
    }

    @PostMapping("/retry-charge")
    public ResponseEntity<BillingStatusDTO> retryCharge() {
        return ResponseEntity.ok(billingService.retryBlockingCharge(loggedCompany()));
    }

    @GetMapping("/charges")
    public ResponseEntity<List<CommissionChargeDTO>> charges() {
        return ResponseEntity.ok(billingService.chargesFor(loggedCompany()));
    }

    // ─── Dados fiscais + notas (NFS-e, Prompt 6) ─────────────────

    @GetMapping("/fiscal-profile")
    public ResponseEntity<CompanyFiscalProfileDTO> fiscalProfile() {
        return ResponseEntity.ok(nfseService.getFiscalProfileDTO(loggedCompany()));
    }

    @PutMapping("/fiscal-profile")
    public ResponseEntity<CompanyFiscalProfileDTO> saveFiscalProfile(
            @RequestBody UpdateCompanyFiscalProfileDTO body) {
        return ResponseEntity.ok(nfseService.saveFiscalProfile(loggedCompany(), body));
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<NfseInvoiceDTO>> invoices() {
        return ResponseEntity.ok(nfseService.invoicesFor(loggedCompany()));
    }

    private Company loggedCompany() {
        UserDTO logged = (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return companyService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company profile not found"));
    }
}
