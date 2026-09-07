package com.main.nexus.controller;

import com.main.nexus.dto.ContractorFinanceOverviewDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.service.CompanyAccessService;
import com.main.nexus.service.FinanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Painel financeiro do contratante (Prompt 7). Agregados do extrato -- as linhas
// detalhadas continuam em /api/company/billing/charges e /invoices.
// /api/company/** já é hasRole("COMPANY").
@RestController
@RequestMapping("/api/company/finance")
public class CompanyFinanceController {

    @Autowired
    private FinanceService financeService;

    @Autowired
    private CompanyAccessService companyAccessService;

    @GetMapping("/overview")
    public ResponseEntity<ContractorFinanceOverviewDTO> overview() {
        return ResponseEntity.ok(financeService.contractorOverview(loggedCompany()));
    }

    // Painel financeiro = OWNER-only (dados financeiros da conta).
    private Company loggedCompany() {
        CompanyAccessService.CompanyAccess access = loggedCompanyAccess();
        companyAccessService.requireOwner(access.role());
        return access.company();
    }

    private CompanyAccessService.CompanyAccess loggedCompanyAccess() {
        UserDTO logged = (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return companyAccessService.resolve(logged);
    }
}
