package com.main.nexus.controller;

import com.main.nexus.dto.ContractorCommissionStatusDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.service.CommissionService;
import com.main.nexus.service.CompanyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Indicador de comissao para o proprio contratante logado -- quantas
// contratacoes gratuitas restam / se ja esta na faixa com comissao. Protegido
// pela regra generica /api/company/** (hasRole("COMPANY")) em SecurityConfig.
@RestController
@RequestMapping("/api/company/commission-status")
public class CommissionController {

    @Autowired
    private CommissionService commissionService;

    @Autowired
    private CompanyService companyService;

    @GetMapping
    public ResponseEntity<ContractorCommissionStatusDTO> myStatus() {
        UserDTO logged = (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        Company company = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company profile not found"));
        return ResponseEntity.ok(commissionService.getContractorStatus(company));
    }
}
