package com.main.nexus.controller;

import com.main.nexus.dto.ContractorCommissionStatusDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.service.CommissionService;
import com.main.nexus.service.CompanyAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Indicador de comissao para o proprio contratante logado -- quantas
// contratacoes gratuitas restam / se ja esta na faixa com comissao. Protegido
// pela regra generica /api/company/** (hasRole("COMPANY")) em SecurityConfig.
@RestController
@RequestMapping("/api/company/commission-status")
public class CommissionController {

    @Autowired
    private CommissionService commissionService;

    @Autowired
    private CompanyAccessService companyAccessService;

    @GetMapping
    public ResponseEntity<ContractorCommissionStatusDTO> myStatus() {
        UserDTO logged = (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        CompanyAccessService.CompanyAccess access = companyAccessService.resolve(logged);
        companyAccessService.requireOwner(access.role()); // indicador financeiro = OWNER-only
        return ResponseEntity.ok(commissionService.getContractorStatus(access.company()));
    }
}
