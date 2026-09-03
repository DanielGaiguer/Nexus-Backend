package com.main.nexus.controller;

import com.main.nexus.dto.AccountDeletionConfirmDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.service.AccountDeletionService;
import com.main.nexus.service.UserDataExportService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Conta do próprio usuário logado: direito de eliminação e de portabilidade (LGPD).
@RestController
@RequestMapping("/api/users")
public class UserAccountController {

    @Autowired
    private AccountDeletionService accountDeletionService;

    @Autowired
    private UserDataExportService userDataExportService;

    // Portabilidade (LGPD art. 18, V) -- JSON estruturado com todos os dados
    // pessoais do próprio titular. Geração síncrona (volume pequeno). Baixado
    // como arquivo; dispara um e-mail de aviso (sinal de segurança).
    @GetMapping("/me/export")
    public ResponseEntity<Map<String, Object>> exportMyData() {
        Map<String, Object> data = userDataExportService.export(loggedUserId());
        String filename = "nexus-data-export-" + loggedUserId() + "-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(data);
    }

    // Pedido de exclusão -- autenticado, só o próprio titular. Não anonimiza
    // agora: dispara o e-mail de confirmação para o endereço original.
    @DeleteMapping("/me")
    public ResponseEntity<Map<String, String>> requestSelfDeletion() {
        accountDeletionService.requestDeletion(loggedUserId());
        return ResponseEntity.accepted().body(Map.of(
                "message", "Enviamos um e-mail com um link para confirmar a exclusão. "
                        + "O link vale 48 horas. Nada é alterado até você confirmar."));
    }

    // Confirmação -- pública: o token do e-mail é a credencial (o link pode ser
    // aberto num dispositivo onde o titular não está logado). Fora do
    // ConsentGateFilter (ver SKIP_PREFIXES).
    @PostMapping("/me/deletion/confirm")
    public ResponseEntity<Map<String, String>> confirmSelfDeletion(
            @RequestBody AccountDeletionConfirmDTO body) {
        if (body == null || body.token() == null || body.token().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirmation token is required.");
        }
        accountDeletionService.confirmDeletion(body.token());
        return ResponseEntity.ok(Map.of(
                "message", "Sua conta foi excluída e seus dados pessoais foram anonimizados."));
    }

    private Long loggedUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDTO user) {
            return user.id();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated.");
    }
}
