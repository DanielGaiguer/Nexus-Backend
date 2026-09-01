package com.main.nexus.controller;

import com.main.nexus.dto.CommissionPolicyDTO;
import com.main.nexus.dto.UpdateCommissionPolicyDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.service.CommissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Configuracao do percentual de comissao pelo Admin. Protegido pela regra
// generica /api/admin/** (hasRole("ADMIN")) em SecurityConfig -- sem regra nova.
@RestController
@RequestMapping("/api/admin/commission-policy")
public class AdminCommissionController {

    @Autowired
    private CommissionService commissionService;

    @GetMapping
    public ResponseEntity<CommissionPolicyDTO> getPolicy() {
        return ResponseEntity.ok(commissionService.getPolicyDTO());
    }

    @PutMapping
    public ResponseEntity<CommissionPolicyDTO> updatePolicy(
            @RequestBody UpdateCommissionPolicyDTO body) {
        return ResponseEntity.ok(
                commissionService.updatePolicy(body.percentage(), loggedUserId()));
    }

    private Long loggedUserId() {
        UserDTO logged = (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return logged.id();
    }
}
