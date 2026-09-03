package com.main.nexus.controller;

import com.main.nexus.dto.LoginRequestDTO;
import com.main.nexus.dto.LoginResponseDTO;
import com.main.nexus.dto.RegisterCompanyLinkedInRequestDTO;
import com.main.nexus.dto.RegisterCompanyRequestDTO;
import com.main.nexus.dto.RegisterProfessionalRequestDTO;
import com.main.nexus.ratelimit.ClientIpResolver;
import com.main.nexus.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private ClientIpResolver clientIpResolver;

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
            @RequestBody LoginRequestDTO request,
            HttpServletRequest httpRequest) {
        // IP passado explicitamente: o bloqueio por falhas de login tem chave
        // IP + e-mail, e o AuthService (um @Service) não enxerga o request.
        LoginResponseDTO response = authService.login(request, clientIpResolver.resolve(httpRequest));
        return ResponseEntity.ok(response);
    }

    // Sign In Linkedin 

    @GetMapping("/linkedin/login")
    public ResponseEntity<Void> linkedInLogin(@RequestParam(required = false) String redirect) {
        return redirect(authService.getLinkedInLoginUrl(redirect)); // Nesse redirect e passado o local onde o usuario estava, para redirecionar depois
    }

    @GetMapping("/linkedin/register")
    public ResponseEntity<Void> linkedInRegister(@RequestParam String role) {
        return redirect(authService.getLinkedInRegisterUrl(role)); // O metodo redirect serve para redirecionar para a URL montada do Linkedin 
    }

    @PostMapping("/register/company/linkedin")
    public ResponseEntity<String> registerCompanyViaLinkedIn(
            @RequestBody RegisterCompanyLinkedInRequestDTO request) {
        authService.registerCompanyViaLinkedIn(request);
        return ResponseEntity.ok("Company registration submitted. Awaiting admin approval.");
    }

    @GetMapping("/linkedin/link")
    public ResponseEntity<Void> linkedInLink(@RequestParam String token) {
        return redirect(authService.getLinkedInLinkUrl(token));
    }

    @GetMapping("/linkedin/callback")
    public ResponseEntity<Void> linkedInCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        return redirect(authService.handleLinkedInCallback(code, state, error));
    }

    private ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }
}