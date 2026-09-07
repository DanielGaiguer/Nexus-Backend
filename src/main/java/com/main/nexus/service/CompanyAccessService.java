package com.main.nexus.service;

import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.CompanyMember;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.CompanyMemberRole;
import com.main.nexus.model.enums.CompanyMemberStatus;
import com.main.nexus.repository.CompanyMemberRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Acesso e composição de membros de uma conta empresarial:
//  - resolve/tryResolve: "quem está autenticado como esta empresa, e com que
//    papel" -- substitui o padrão espalhado companyService.findByUserId(logged.id())
//    + suposição implícita de OWNER. Só considera vínculo ACTIVE.
//  - requireOwner/requireMember: guard de papel num único ponto.
//  - isActiveMember / operationalRecipients / ownerRecipients: quem é participante
//    (chat) e quem recebe cada categoria de notificação da empresa.
//
// Enquanto só existir 1 OWNER ACTIVE por empresa (backfill + cadastro), tudo isto
// se comporta exatamente como antes: resolve() devolve OWNER, os guards passam,
// e as listas de destinatários têm um único elemento (o antigo company.getUser()).
//
// resolve(...) lança 404 "Company profile not found" -- mesma resposta que os
// call sites davam antes com companyService.findByUserId(...).orElseThrow(...).
// tryResolve(...) devolve Optional para os pontos que tratam a ausência sem lançar
// (viewer do mapa / do perfil público, badges da sidebar).
@Service
public class CompanyAccessService {

    @Autowired
    private CompanyMemberRepository companyMemberRepository;

    // Company + papel do usuário logado dentro dela (sempre um vínculo ACTIVE).
    public record CompanyAccess(Company company, CompanyMemberRole role) {}

    public CompanyAccess resolve(UserDTO logged) {
        return resolve(logged.id());
    }

    public CompanyAccess resolve(Long userId) {
        return tryResolve(userId).orElseThrow(() -> new ResponseStatusException(
                HttpStatusCode.valueOf(404), "Company profile not found"));
    }

    public Optional<CompanyAccess> tryResolve(UserDTO logged) {
        return tryResolve(logged.id());
    }

    public Optional<CompanyAccess> tryResolve(Long userId) {
        return companyMemberRepository.findByUserIdAndStatus(userId, CompanyMemberStatus.ACTIVE)
                .map(member -> new CompanyAccess(member.getCompany(), member.getRole()));
    }

    // ── Guards de papel ──────────────────────────────────────────────
    // 403 no mesmo formato dos ownership checks já espalhados pelos services.
    // Hoje todo membro é OWNER, então requireOwner nunca barra ninguém.

    public void requireOwner(CompanyMemberRole role) {
        if (role != CompanyMemberRole.OWNER) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This action is restricted to the account owner.");
        }
    }

    public void requireMember(CompanyMemberRole role) {
        if (role == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This action requires an active company membership.");
        }
    }

    // ── Participação e destinatários de notificação ──────────────────

    // Autorização de chat do match e afins: o usuário é um membro ACTIVE da
    // empresa dona? (Substitui a checagem antiga "userId == company.getUser().getId()".)
    public boolean isActiveMember(Long companyId, Long userId) {
        return companyMemberRepository.existsByCompanyIdAndUserIdAndStatus(
                companyId, userId, CompanyMemberStatus.ACTIVE);
    }

    // Notificação OPERACIONAL (recrutamento): todo membro ACTIVE.
    public List<User> operationalRecipients(Company company) {
        return companyMemberRepository
                .findByCompanyIdAndStatus(company.getId(), CompanyMemberStatus.ACTIVE)
                .stream().map(CompanyMember::getUser).toList();
    }

    // Notificação FINANCEIRA / FISCAL / DE CONTA: só OWNER ACTIVE.
    public List<User> ownerRecipients(Company company) {
        return companyMemberRepository
                .findByCompanyIdAndStatusAndRole(
                        company.getId(), CompanyMemberStatus.ACTIVE, CompanyMemberRole.OWNER)
                .stream().map(CompanyMember::getUser).toList();
    }
}
