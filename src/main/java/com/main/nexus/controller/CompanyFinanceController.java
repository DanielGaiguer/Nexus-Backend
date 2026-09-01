package com.main.nexus.controller;

import com.main.nexus.dto.ContractorFinanceOverviewDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.FinanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Painel financeiro do contratante (Prompt 7). Agregados do extrato -- as linhas
// detalhadas continuam em /api/company/billing/charges e /invoices.
// /api/company/** já é hasRole("COMPANY").
@RestController
@RequestMapping("/api/company/finance")
public class CompanyFinanceController {

    @Autowired
    private FinanceService financeService;

    @Autowired
    private CompanyService companyService;

    @GetMapping("/overview")
    public ResponseEntity<ContractorFinanceOverviewDTO> overview() {
        return ResponseEntity.ok(financeService.contractorOverview(loggedCompany()));
    }

    private Company loggedCompany() {
        UserDTO logged = (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return companyService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company profile not found"));
    }
}
