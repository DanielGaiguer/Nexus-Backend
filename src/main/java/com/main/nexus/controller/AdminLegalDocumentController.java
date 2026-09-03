package com.main.nexus.controller;

import com.main.nexus.dto.AdminLegalOverviewDTO;
import com.main.nexus.dto.LegalDocumentDTO;
import com.main.nexus.dto.PublishLegalDocumentDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.enums.LegalDocumentType;
import com.main.nexus.service.LegalDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Painel do Admin: publicar nova versao de Termos ou Politica. Protegido pela
// regra generica /api/admin/** (hasRole("ADMIN")) em SecurityConfig.
//
// Publicar uma nova versao dos TERMOS leva todo usuario cujo ultimo aceite seja
// de versao anterior a uma tela de re-aceite obrigatorio no proximo acesso
// (ConsentGateFilter no backend + gate no layout autenticado do frontend).
@RestController
@RequestMapping("/api/admin/legal-documents")
public class AdminLegalDocumentController {

    @Autowired
    private LegalDocumentService legalDocumentService;

    @GetMapping
    public ResponseEntity<AdminLegalOverviewDTO> overview() {
        return ResponseEntity.ok(legalDocumentService.adminOverview());
    }

    @PostMapping("/{slug}/versions")
    public ResponseEntity<LegalDocumentDTO> publish(@PathVariable String slug,
                                                    @RequestBody PublishLegalDocumentDTO body) {
        LegalDocumentType type = parse(slug);
        LegalDocumentDTO published = legalDocumentService.publishNewVersion(
                type, body.content(), body.summaryOfChanges(), loggedUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(published);
    }

    private LegalDocumentType parse(String slug) {
        try {
            return LegalDocumentType.fromSlug(slug);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown legal document: " + slug);
        }
    }

    private Long loggedUserId() {
        UserDTO logged = (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return logged.id();
    }
}
