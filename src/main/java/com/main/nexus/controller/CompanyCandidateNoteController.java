package com.main.nexus.controller;

import com.main.nexus.dto.CompanyCandidateNoteDTO;
import com.main.nexus.dto.CompanyCandidateNoteRequestDTO;
import com.main.nexus.dto.CompanyCandidateNoteUpdateDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.service.CompanyAccessService;
import com.main.nexus.service.CompanyCandidateNoteService;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Notas internas de recrutamento sobre um profissional. Sob /api/company/** -> hasRole("COMPANY")
// no SecurityConfig: um profissional autenticado NUNCA chega aqui (403 na cadeia de filtros).
// Sem requireOwner -- criar/listar é livre para qualquer membro ACTIVE (régua de recrutamento).
// A régua de edição/exclusão (autor original ou OWNER) fica no CompanyCandidateNoteService.
@RestController
@RequestMapping("/api/company/candidates/{professionalId}/notes")
public class CompanyCandidateNoteController {

    @Autowired
    private CompanyCandidateNoteService noteService;

    @Autowired
    private CompanyAccessService companyAccessService;

    @GetMapping
    public ResponseEntity<List<CompanyCandidateNoteDTO>> list(
            @PathVariable Long professionalId,
            @RequestParam(required = false) Long matchId) {
        UserDTO logged = getLoggedUser();
        CompanyAccessService.CompanyAccess access = companyAccessService.resolve(logged);
        return ResponseEntity.ok(noteService.list(
                access.company().getId(), access.role(), logged.id(), professionalId, matchId));
    }

    @PostMapping
    public ResponseEntity<CompanyCandidateNoteDTO> create(
            @PathVariable Long professionalId,
            @RequestBody CompanyCandidateNoteRequestDTO body) {
        UserDTO logged = getLoggedUser();
        CompanyAccessService.CompanyAccess access = companyAccessService.resolve(logged);
        return ResponseEntity.ok(noteService.create(
                access.company().getId(), access.role(), logged.id(), professionalId, body));
    }

    @PutMapping("/{noteId}")
    public ResponseEntity<CompanyCandidateNoteDTO> update(
            @PathVariable Long professionalId,
            @PathVariable Long noteId,
            @RequestBody CompanyCandidateNoteUpdateDTO body) {
        UserDTO logged = getLoggedUser();
        CompanyAccessService.CompanyAccess access = companyAccessService.resolve(logged);
        return ResponseEntity.ok(noteService.update(
                access.company().getId(), access.role(), logged.id(), professionalId, noteId, body));
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Long professionalId,
            @PathVariable Long noteId) {
        UserDTO logged = getLoggedUser();
        CompanyAccessService.CompanyAccess access = companyAccessService.resolve(logged);
        noteService.delete(
                access.company().getId(), access.role(), logged.id(), professionalId, noteId);
        return ResponseEntity.ok(Map.of("message", "Note deleted."));
    }

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
