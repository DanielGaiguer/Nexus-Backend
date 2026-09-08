package com.main.nexus.service;

import com.main.nexus.dto.CompanyCandidateNoteDTO;
import com.main.nexus.dto.CompanyCandidateNoteRequestDTO;
import com.main.nexus.dto.CompanyCandidateNoteUpdateDTO;
import com.main.nexus.model.CompanyCandidateNote;
import com.main.nexus.model.CompanyMember;
import com.main.nexus.model.Match;
import com.main.nexus.model.Professional;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.CompanyMemberRole;
import com.main.nexus.model.enums.CompanyMemberStatus;
import com.main.nexus.repository.CompanyCandidateNoteRepository;
import com.main.nexus.repository.CompanyMemberRepository;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.ProfessionalRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Notas internas de recrutamento sobre um profissional (Passo 2 do Kanban de contratação).
//
// ─── PRIVACIDADE ────────────────────────────────────────────────────────────────────────────
// Esta entidade é DELIBERADAMENTE company-only. Nenhum endpoint/DTO acessível ao profissional
// (MatchController, ScreeningInvitationController, o PDF de PdfService.generateProfessionalProfile,
// o export LGPD de UserDataExportService) referencia CompanyCandidateNote. A regressão está
// travada por CompanyCandidateNoteIsolationTest.
//
// A pergunta em aberto: notas internas de recrutador são opinião sobre uma pessoa física, então
// PODEM entrar no escopo do direito de acesso/portabilidade da LGPD (art. 18) que
// UserDataExportService implementa. A decisão de INCLUIR OU NÃO no export ainda NÃO foi tomada --
// está pendente de revisão jurídica, na mesma lista da minuta de Termos/Política. Ver o comentário
// espelho em UserDataExportService. Por ora: fora do export.
//
// ─── QUEM PODE O QUÊ ────────────────────────────────────────────────────────────────────────
//  - Criar / listar: qualquer CompanyMember ACTIVE da empresa (sem requireOwner -- é operação de
//    recrutamento, mesma régua de Project/Match). A LISTAGEM sempre traz as notas de TODOS os
//    membros -- transparência de equipe.
//  - Editar / apagar: só o AUTOR ORIGINAL da nota, OU um OWNER da conta (moderação). Um MEMBER
//    não-autor recebe 403 ao tentar mexer na nota de outro. Nota com author == null (membro
//    removido da conta): só OWNER pode editar/apagar.
@Service
public class CompanyCandidateNoteService {

    @Autowired
    private CompanyCandidateNoteRepository noteRepository;

    @Autowired
    private CompanyMemberRepository companyMemberRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private MatchRepository matchRepository;

    // ─── Leitura ────────────────────────────────────────────────────

    @Transactional
    public List<CompanyCandidateNoteDTO> list(
            Long companyId, CompanyMemberRole viewerRole, Long viewerUserId,
            Long professionalId, Long matchIdFilter) {

        CompanyMember viewer = requireActiveMember(viewerUserId);
        requireProfessional(professionalId);

        List<CompanyCandidateNote> notes = matchIdFilter != null
                ? noteRepository.findByCompanyIdAndProfessionalIdAndMatchIdOrderByCreatedAtDesc(
                        companyId, professionalId, matchIdFilter)
                : noteRepository.findByCompanyIdAndProfessionalIdOrderByCreatedAtDesc(
                        companyId, professionalId);

        return notes.stream()
                .map(note -> toDTO(note, viewer.getId(), viewerRole))
                .toList();
    }

    // ─── Criação ────────────────────────────────────────────────────

    @Transactional
    public CompanyCandidateNoteDTO create(
            Long companyId, CompanyMemberRole viewerRole, Long viewerUserId,
            Long professionalId, CompanyCandidateNoteRequestDTO request) {

        CompanyMember author = requireActiveMember(viewerUserId);
        Professional professional = requireProfessional(professionalId);
        String body = requireBody(request != null ? request.body() : null);

        CompanyCandidateNote note = new CompanyCandidateNote();
        note.setCompany(author.getCompany());
        note.setProfessional(professional);
        note.setAuthor(author);
        // Snapshot da identidade do autor -- nunca relido do CompanyMember depois.
        note.setAuthorLabel(labelFor(author));
        note.setBody(body);

        Long matchId = request != null ? request.matchId() : null;
        if (matchId != null) {
            Match match = matchRepository.findById(matchId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatusCode.valueOf(404), "Match not found: " + matchId));
            if (!match.getProject().getCompany().getId().equals(companyId)) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                        "This match does not belong to your company.");
            }
            if (!match.getProfessional().getId().equals(professionalId)) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "This match is not about the professional in the path.");
            }
            note.setMatch(match);
        }

        LocalDateTime now = LocalDateTime.now();
        note.setCreatedAt(now);
        note.setUpdatedAt(now);

        CompanyCandidateNote saved = noteRepository.save(note);
        return toDTO(saved, author.getId(), viewerRole);
    }

    // ─── Edição ─────────────────────────────────────────────────────

    @Transactional
    public CompanyCandidateNoteDTO update(
            Long companyId, CompanyMemberRole viewerRole, Long viewerUserId,
            Long professionalId, Long noteId, CompanyCandidateNoteUpdateDTO request) {

        CompanyMember viewer = requireActiveMember(viewerUserId);
        CompanyCandidateNote note = requireNote(noteId, companyId, professionalId);
        requireCanModify(note, viewer.getId(), viewerRole);

        note.setBody(requireBody(request != null ? request.body() : null));
        note.setUpdatedAt(LocalDateTime.now());

        return toDTO(noteRepository.save(note), viewer.getId(), viewerRole);
    }

    // ─── Exclusão ───────────────────────────────────────────────────

    @Transactional
    public void delete(
            Long companyId, CompanyMemberRole viewerRole, Long viewerUserId,
            Long professionalId, Long noteId) {

        CompanyMember viewer = requireActiveMember(viewerUserId);
        CompanyCandidateNote note = requireNote(noteId, companyId, professionalId);
        requireCanModify(note, viewer.getId(), viewerRole);

        noteRepository.delete(note);
    }

    // ─── Regras ─────────────────────────────────────────────────────

    // Autor original OU OWNER. Autor ausente (membro removido) => só OWNER.
    private void requireCanModify(CompanyCandidateNote note, Long viewerMemberId, CompanyMemberRole viewerRole) {
        boolean isAuthor = note.getAuthor() != null
                && note.getAuthor().getId() != null
                && note.getAuthor().getId().equals(viewerMemberId);
        boolean isOwner = viewerRole == CompanyMemberRole.OWNER;
        if (!isAuthor && !isOwner) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "Only the note's author or an account owner can edit or delete it.");
        }
    }

    private CompanyMember requireActiveMember(Long userId) {
        return companyMemberRepository.findByUserIdAndStatus(userId, CompanyMemberStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(403), "Active company membership required."));
    }

    private Professional requireProfessional(Long professionalId) {
        return professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional not found: " + professionalId));
    }

    // Escopa a nota à empresa E ao profissional da rota -- fora disso, 404 (não vaza a existência).
    private CompanyCandidateNote requireNote(Long noteId, Long companyId, Long professionalId) {
        CompanyCandidateNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Note not found: " + noteId));
        if (!note.getCompany().getId().equals(companyId)
                || !note.getProfessional().getId().equals(professionalId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), "Note not found: " + noteId);
        }
        return note;
    }

    private String requireBody(String body) {
        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), "'body' is required.");
        }
        return body.trim();
    }

    private String labelFor(CompanyMember member) {
        User user = member.getUser();
        return user != null && user.getEmail() != null ? user.getEmail() : "membro #" + member.getId();
    }

    private CompanyCandidateNoteDTO toDTO(
            CompanyCandidateNote note, Long viewerMemberId, CompanyMemberRole viewerRole) {
        CompanyMember author = note.getAuthor();
        boolean canEdit = (author != null && author.getId() != null
                        && author.getId().equals(viewerMemberId))
                || viewerRole == CompanyMemberRole.OWNER;
        return new CompanyCandidateNoteDTO(
                note.getId(),
                note.getProfessional().getId(),
                note.getMatch() != null ? note.getMatch().getId() : null,
                author != null ? author.getId() : null,
                author != null && author.getUser() != null ? author.getUser().getId() : null,
                note.getAuthorLabel(),
                canEdit,
                note.getBody(),
                note.getCreatedAt(),
                note.getUpdatedAt());
    }
}
