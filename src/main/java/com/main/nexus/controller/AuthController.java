package com.main.nexus.controller;

import com.main.nexus.dto.LoginRequestDTO;
import com.main.nexus.dto.LoginResponseDTO;
import com.main.nexus.dto.RegisterCompanyRequestDTO;
import com.main.nexus.dto.RegisterProfessionalRequestDTO;
import com.main.nexus.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register/professional")
    public ResponseEntity<String> registerProfessional(
            @RequestBody RegisterProfessionalRequestDTO request) {
        authService.registerProfessional(request);
        return ResponseEntity.ok("Professional registered successfully.");
    }

    @PostMapping("/register/company")
    public ResponseEntity<String> registerCompany(
            @RequestBody RegisterCompanyRequestDTO request) {
        authService.registerCompany(request);
        return ResponseEntity.ok("Company registration submitted. Awaiting admin approval.");
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(
            @RequestBody LoginRequestDTO request) {
        LoginResponseDTO response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}