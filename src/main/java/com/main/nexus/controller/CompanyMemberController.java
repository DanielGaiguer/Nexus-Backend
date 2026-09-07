package com.main.nexus.controller;

import com.main.nexus.dto.AcceptCompanyInvitationDTO;
import com.main.nexus.dto.CompanyInvitationDTO;
import com.main.nexus.dto.CompanyMembersResponseDTO;
import com.main.nexus.dto.InviteMemberRequestDTO;
import com.main.nexus.dto.LoginResponseDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.service.CompanyAccessService;
import com.main.nexus.service.CompanyMemberService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Gestão de membros de uma conta empresarial. Tudo OWNER-only (regra genérica
// /api/company/** = hasRole("COMPANY") no SecurityConfig + requireOwner aqui),
// EXCETO POST /api/company/invitations/accept, que é público (o token do e-mail
// é a credencial) -- liberado no SecurityConfig e no ConsentGateFilter, mesmo
// padrão de /api/users/me/deletion/confirm.
@RestController
@RequestMapping("/api/company")
public class CompanyMemberController {

    @Autowired
    private CompanyMemberService companyMemberService;

    @Autowired
    private CompanyAccessService companyAccessService;

    @GetMapping("/members")
    public ResponseEntity<CompanyMembersResponseDTO> listMembers() {
        return ResponseEntity.ok(companyMemberService.list(ownerCompany().company()));
    }

    @PostMapping("/members/invite")
    public ResponseEntity<CompanyInvitationDTO> invite(@RequestBody InviteMemberRequestDTO body) {
        CompanyAccessService.CompanyAccess access = ownerCompany();
        return ResponseEntity.ok(companyMemberService.invite(
                access.company(), loggedUserId(), body != null ? body.email() : null));
    }

    @DeleteMapping("/members/{id}")
    public ResponseEntity<Map<String, String>> removeMember(@PathVariable Long id) {
        companyMemberService.removeMember(ownerCompany().company(), id);
        return ResponseEntity.ok(Map.of("message", "Member removed."));
    }

    @PostMapping("/members/{id}/transfer-ownership")
    public ResponseEntity<Map<String, String>> transferOwnership(@PathVariable Long id) {
        CompanyAccessService.CompanyAccess access = ownerCompany();
        companyMemberService.transferOwnership(access.company(), loggedUserId(), id);
        return ResponseEntity.ok(Map.of("message", "Ownership transferred."));
    }

    @DeleteMapping("/invitations/{id}")
    public ResponseEntity<Map<String, String>> revokeInvitation(@PathVariable Long id) {
        companyMemberService.revokeInvitation(ownerCompany().company(), id);
        return ResponseEntity.ok(Map.of("message", "Invitation revoked."));
    }

    // Público -- ver comentário da classe.
    @PostMapping("/invitations/accept")
    public ResponseEntity<LoginResponseDTO> acceptInvitation(
            @RequestBody AcceptCompanyInvitationDTO body) {
        return ResponseEntity.ok(companyMemberService.acceptInvitation(body));
    }

    // ── helpers ─────────────────────────────────────────────────────

    private Long loggedUserId() {
        return loggedUser().id();
    }

    private UserDTO loggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }

    // Resolve a empresa do usuário logado e exige papel OWNER.
    private CompanyAccessService.CompanyAccess ownerCompany() {
        CompanyAccessService.CompanyAccess access = companyAccessService.resolve(loggedUser());
        companyAccessService.requireOwner(access.role());
        return access;
    }
}
