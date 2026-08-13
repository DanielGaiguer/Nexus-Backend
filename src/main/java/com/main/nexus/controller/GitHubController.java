package com.main.nexus.controller;

import com.main.nexus.dto.UserDTO;
import com.main.nexus.service.AuthService;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Espelha o fluxo de LinkedIn
// simples redirects HTTP 302 primeiro para o GitHub, depois de volta para o frontend, com
// o "code" trocado por token/usuário inteiramente no servidor. Só o unlink foge desse desenho,
// por não envolver OAuth: é uma ação autenticada comum, resolvida na hora.
@RestController
@RequestMapping("/api/auth/github")
public class GitHubController {

    @Autowired
    private AuthService authService;

    @GetMapping("/login")
    public ResponseEntity<Void> login(@RequestParam(required = false) String redirect) {
        return redirect(authService.getGitHubLoginUrl(redirect));
    }

    @GetMapping("/register")
    public ResponseEntity<Void> register() {
        return redirect(authService.getGitHubRegisterUrl());
    }

    @GetMapping("/link")
    public ResponseEntity<Void> link(@RequestParam String token) {
        return redirect(authService.getGitHubLinkUrl(token));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        return redirect(authService.handleGitHubCallback(code, state, error));
    }

    @DeleteMapping("/unlink")
    public ResponseEntity<String> unlink() {
        UserDTO logged = getLoggedUser();
        authService.unlinkGitHub(logged.id());
        return ResponseEntity.ok("GitHub account unlinked.");
    }

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    private ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }
}
