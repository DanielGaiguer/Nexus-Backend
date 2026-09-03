package com.main.nexus.controller;

import com.main.nexus.dto.LegalDocumentDTO;
import com.main.nexus.dto.LegalDocumentVersionDTO;
import com.main.nexus.model.enums.LegalDocumentType;
import com.main.nexus.service.LegalDocumentService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Leitura publica dos documentos legais (Termos de Uso / Politica de
// Privacidade). Publico pela regra generica /api/public/** em SecurityConfig.
// {slug} = "terms" | "privacy".
@RestController
@RequestMapping("/api/public/legal")
public class LegalDocumentController {

    @Autowired
    private LegalDocumentService legalDocumentService;

    @GetMapping("/{slug}")
    public ResponseEntity<LegalDocumentDTO> active(@PathVariable String slug) {
        return ResponseEntity.ok(legalDocumentService.active(parse(slug)));
    }

    @GetMapping("/{slug}/versions")
    public ResponseEntity<List<LegalDocumentVersionDTO>> versions(@PathVariable String slug) {
        return ResponseEntity.ok(legalDocumentService.versions(parse(slug)));
    }

    @GetMapping("/{slug}/versions/{version}")
    public ResponseEntity<LegalDocumentDTO> version(@PathVariable String slug,
                                                    @PathVariable int version) {
        return ResponseEntity.ok(legalDocumentService.version(parse(slug), version));
    }

    private LegalDocumentType parse(String slug) {
        try {
            return LegalDocumentType.fromSlug(slug);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown legal document: " + slug);
        }
    }
}
