package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.main.nexus.dto.CompanyCandidateNoteDTO;
import com.main.nexus.dto.CompanyCandidateNoteRequestDTO;
import com.main.nexus.dto.CompanyCandidateNoteUpdateDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.CompanyCandidateNote;
import com.main.nexus.model.CompanyMember;
import com.main.nexus.model.Match;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.CompanyMemberRole;
import com.main.nexus.model.enums.CompanyMemberStatus;
import com.main.nexus.repository.CompanyCandidateNoteRepository;
import com.main.nexus.repository.CompanyMemberRepository;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.ProfessionalRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanyCandidateNoteServiceTest {

    private static final Long COMPANY_ID = 10L;
    private static final Long PROF_ID = 1L;

    @Mock private CompanyCandidateNoteRepository noteRepository;
    @Mock private CompanyMemberRepository companyMemberRepository;
    @Mock private ProfessionalRepository professionalRepository;
    @Mock private MatchRepository matchRepository;

    @InjectMocks private CompanyCandidateNoteService service;

    private Company company;
    private Professional professional;
    private CompanyMember memberA; // user 7, MEMBER
    private CompanyMember memberB; // user 8, MEMBER
    private CompanyMember ownerC;  // user 9, OWNER

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(COMPANY_ID);

        professional = new Professional();
        professional.setId(PROF_ID);
        professional.setName("Ana Dev");

        memberA = member(100L, 7L, "a@acme.com", CompanyMemberRole.MEMBER);
        memberB = member(200L, 8L, "b@acme.com", CompanyMemberRole.MEMBER);
        ownerC = member(300L, 9L, "c@acme.com", CompanyMemberRole.OWNER);

        when(professionalRepository.findById(PROF_ID)).thenReturn(Optional.of(professional));
        when(companyMemberRepository.findByUserIdAndStatus(7L, CompanyMemberStatus.ACTIVE))
                .thenReturn(Optional.of(memberA));
        when(companyMemberRepository.findByUserIdAndStatus(8L, CompanyMemberStatus.ACTIVE))
                .thenReturn(Optional.of(memberB));
        when(companyMemberRepository.findByUserIdAndStatus(9L, CompanyMemberStatus.ACTIVE))
                .thenReturn(Optional.of(ownerC));
        when(noteRepository.save(any(CompanyCandidateNote.class))).thenAnswer(inv -> {
            CompanyCandidateNote n = inv.getArgument(0);
            if (n.getId() == null) {
                n.setId(999L);
            }
            return n;
        });
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private CompanyMember member(Long id, Long userId, String email, CompanyMemberRole role) {
        User u = new User();
        u.setId(userId);
        u.setEmail(email);
        CompanyMember m = new CompanyMember();
        m.setId(id);
        m.setCompany(company);
        m.setUser(u);
        m.setRole(role);
        m.setStatus(CompanyMemberStatus.ACTIVE);
        return m;
    }

    private Match matchOwnedBy(Long companyId, Long professionalId) {
        Company c = new Company();
        c.setId(companyId);
        Project p = new Project();
        p.setId(50L);
        p.setCompany(c);
        Professional prof = new Professional();
        prof.setId(professionalId);
        Match m = new Match();
        m.setId(500L);
        m.setProject(p);
        m.setProfessional(prof);
        return m;
    }

    private CompanyCandidateNote note(Long id, CompanyMember author, String label) {
        CompanyCandidateNote n = new CompanyCandidateNote();
        n.setId(id);
        n.setCompany(company);
        n.setProfessional(professional);
        n.setAuthor(author);
        n.setAuthorLabel(label);
        n.setBody("texto original");
        return n;
    }

    // ─── Criação ────────────────────────────────────────────────────

    @Test
    void create_snapshotsAuthorLabelAndTrimsBody() {
        CompanyCandidateNoteDTO dto = service.create(COMPANY_ID, CompanyMemberRole.MEMBER, 7L, PROF_ID,
                new CompanyCandidateNoteRequestDTO(null, "  boa comunicação  "));

        ArgumentCaptor<CompanyCandidateNote> captor = ArgumentCaptor.forClass(CompanyCandidateNote.class);
        verify(noteRepository).save(captor.capture());
        CompanyCandidateNote saved = captor.getValue();
        assertEquals("a@acme.com", saved.getAuthorLabel());
        assertSame(memberA, saved.getAuthor());
        assertSame(company, saved.getCompany());
        assertSame(professional, saved.getProfessional());
        assertEquals("boa comunicação", saved.getBody());
        assertNull(saved.getMatch());

        assertTrue(dto.canEdit());
        assertEquals(100L, dto.authorMemberId());
        assertEquals(7L, dto.authorUserId());
        assertEquals("a@acme.com", dto.authorLabel());
    }

    @Test
    void create_withValidMatch_setsContext() {
        Match match = matchOwnedBy(COMPANY_ID, PROF_ID);
        when(matchRepository.findById(500L)).thenReturn(Optional.of(match));

        service.create(COMPANY_ID, CompanyMemberRole.MEMBER, 7L, PROF_ID,
                new CompanyCandidateNoteRequestDTO(500L, "durante a entrevista"));

        ArgumentCaptor<CompanyCandidateNote> captor = ArgumentCaptor.forClass(CompanyCandidateNote.class);
        verify(noteRepository).save(captor.capture());
        assertSame(match, captor.getValue().getMatch());
    }

    @Test
    void create_withMatchFromAnotherCompany_rejected() {
        when(matchRepository.findById(500L)).thenReturn(Optional.of(matchOwnedBy(999L, PROF_ID)));

        assertThrows(ResponseStatusException.class, () -> service.create(
                COMPANY_ID, CompanyMemberRole.MEMBER, 7L, PROF_ID,
                new CompanyCandidateNoteRequestDTO(500L, "x")));
        verify(noteRepository, never()).save(any());
    }

    @Test
    void create_withMatchForAnotherProfessional_rejected() {
        when(matchRepository.findById(500L)).thenReturn(Optional.of(matchOwnedBy(COMPANY_ID, 2L)));

        assertThrows(ResponseStatusException.class, () -> service.create(
                COMPANY_ID, CompanyMemberRole.MEMBER, 7L, PROF_ID,
                new CompanyCandidateNoteRequestDTO(500L, "x")));
        verify(noteRepository, never()).save(any());
    }

    @Test
    void create_blankBody_rejected() {
        assertThrows(ResponseStatusException.class, () -> service.create(
                COMPANY_ID, CompanyMemberRole.MEMBER, 7L, PROF_ID,
                new CompanyCandidateNoteRequestDTO(null, "   ")));
    }

    @Test
    void create_professionalNotFound_404() {
        when(professionalRepository.findById(PROF_ID)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.create(
                COMPANY_ID, CompanyMemberRole.MEMBER, 7L, PROF_ID,
                new CompanyCandidateNoteRequestDTO(null, "x")));
    }

    @Test
    void create_notActiveMember_403() {
        when(companyMemberRepository.findByUserIdAndStatus(7L, CompanyMemberStatus.ACTIVE))
                .thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.create(
                COMPANY_ID, CompanyMemberRole.MEMBER, 7L, PROF_ID,
                new CompanyCandidateNoteRequestDTO(null, "x")));
        verify(noteRepository, never()).save(any());
    }

    // ─── Listagem (transparência de equipe + canEdit por viewer) ────

    @Test
    void list_showsAllMembersNotes_withPerViewerCanEdit() {
        CompanyCandidateNote mine = note(1L, memberA, "a@acme.com");
        CompanyCandidateNote hers = note(2L, memberB, "b@acme.com");
        when(noteRepository.findByCompanyIdAndProfessionalIdOrderByCreatedAtDesc(COMPANY_ID, PROF_ID))
                .thenReturn(List.of(mine, hers));

        List<CompanyCandidateNoteDTO> result =
                service.list(COMPANY_ID, CompanyMemberRole.MEMBER, 7L, PROF_ID, null);

        assertEquals(2, result.size());
        assertTrue(result.get(0).canEdit());   // memberA olhando a própria nota
        assertFalse(result.get(1).canEdit());  // memberA não pode editar a de memberB
        assertEquals("b@acme.com", result.get(1).authorLabel());
    }

    @Test
    void list_ownerCanEditEveryNote() {
        when(noteRepository.findByCompanyIdAndProfessionalIdOrderByCreatedAtDesc(COMPANY_ID, PROF_ID))
                .thenReturn(List.of(note(1L, memberA, "a@acme.com")));

        List<CompanyCandidateNoteDTO> result =
                service.list(COMPANY_ID, CompanyMemberRole.OWNER, 9L, PROF_ID, null);

        assertTrue(result.get(0).canEdit());
    }

    @Test
    void list_withMatchFilter_usesFilteredQuery() {
        when(noteRepository.findByCompanyIdAndProfessionalIdAndMatchIdOrderByCreatedAtDesc(
                COMPANY_ID, PROF_ID, 500L)).thenReturn(List.of());

        service.list(COMPANY_ID, CompanyMemberRole.MEMBER, 7L, PROF_ID, 500L);

        verify(noteRepository).findByCompanyIdAndProfessionalIdAndMatchIdOrderByCreatedAtDesc(
                COMPANY_ID, PROF_ID, 500L);
        verify(noteRepository, never())
                .findByCompanyIdAndProfessionalIdOrderByCreatedAtDesc(anyLong(), anyLong());
    }

    // ─── Edição ─────────────────────────────────────────────────────

    @Test
    void update_authorEditsOwnNote() {
        CompanyCandidateNote n = note(55L, memberA, "a@acme.com");
        when(noteRepository.findById(55L)).thenReturn(Optional.of(n));

        CompanyCandidateNoteDTO dto = service.update(COMPANY_ID, CompanyMemberRole.MEMBER, 7L, PROF_ID,
                55L, new CompanyCandidateNoteUpdateDTO("texto revisado"));

        assertEquals("texto revisado", n.getBody());
        assertEquals("texto revisado", dto.body());
        verify(noteRepository).save(n);
    }

    @Test
    void update_memberCannotEditAnothersNote() {
        CompanyCandidateNote n = note(55L, memberA, "a@acme.com");
        when(noteRepository.findById(55L)).thenReturn(Optional.of(n));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.update(COMPANY_ID, CompanyMemberRole.MEMBER, 8L, PROF_ID,
                        55L, new CompanyCandidateNoteUpdateDTO("hack")));
        assertEquals(403, ex.getStatusCode().value());
        verify(noteRepository, never()).save(any());
    }

    @Test
    void update_ownerCanEditAnothersNote() {
        CompanyCandidateNote n = note(55L, memberA, "a@acme.com");
        when(noteRepository.findById(55L)).thenReturn(Optional.of(n));

        service.update(COMPANY_ID, CompanyMemberRole.OWNER, 9L, PROF_ID,
                55L, new CompanyCandidateNoteUpdateDTO("moderado pelo owner"));

        assertEquals("moderado pelo owner", n.getBody());
        verify(noteRepository).save(n);
    }

    @Test
    void update_authorNull_memberBlocked_ownerAllowed() {
        CompanyCandidateNote orphan = note(55L, null, "ex-membro@acme.com");
        when(noteRepository.findById(55L)).thenReturn(Optional.of(orphan));

        assertThrows(ResponseStatusException.class,
                () -> service.update(COMPANY_ID, CompanyMemberRole.MEMBER, 7L, PROF_ID,
                        55L, new CompanyCandidateNoteUpdateDTO("x")));

        service.update(COMPANY_ID, CompanyMemberRole.OWNER, 9L, PROF_ID,
                55L, new CompanyCandidateNoteUpdateDTO("owner corrige"));
        assertEquals("owner corrige", orphan.getBody());
    }

    @Test
    void update_noteFromAnotherCompany_404() {
        CompanyCandidateNote n = note(55L, memberA, "a@acme.com");
        Company other = new Company();
        other.setId(999L);
        n.setCompany(other);
        when(noteRepository.findById(55L)).thenReturn(Optional.of(n));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.update(COMPANY_ID, CompanyMemberRole.OWNER, 9L, PROF_ID,
                        55L, new CompanyCandidateNoteUpdateDTO("x")));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void update_noteForAnotherProfessional_404() {
        CompanyCandidateNote n = note(55L, memberA, "a@acme.com");
        Professional other = new Professional();
        other.setId(2L);
        n.setProfessional(other);
        when(noteRepository.findById(55L)).thenReturn(Optional.of(n));

        assertThrows(ResponseStatusException.class,
                () -> service.update(COMPANY_ID, CompanyMemberRole.OWNER, 9L, PROF_ID,
                        55L, new CompanyCandidateNoteUpdateDTO("x")));
    }

    @Test
    void update_blankBody_400() {
        CompanyCandidateNote n = note(55L, memberA, "a@acme.com");
        when(noteRepository.findById(55L)).thenReturn(Optional.of(n));

        assertThrows(ResponseStatusException.class,
                () -> service.update(COMPANY_ID, CompanyMemberRole.MEMBER, 7L, PROF_ID,
                        55L, new CompanyCandidateNoteUpdateDTO("  ")));
    }

    // ─── Exclusão ───────────────────────────────────────────────────

    @Test
    void delete_authorDeletesOwnNote() {
        CompanyCandidateNote n = note(55L, memberA, "a@acme.com");
        when(noteRepository.findById(55L)).thenReturn(Optional.of(n));

        service.delete(COMPANY_ID, CompanyMemberRole.MEMBER, 7L, PROF_ID, 55L);

        verify(noteRepository).delete(n);
    }

    @Test
    void delete_memberCannotDeleteAnothersNote() {
        CompanyCandidateNote n = note(55L, memberA, "a@acme.com");
        when(noteRepository.findById(55L)).thenReturn(Optional.of(n));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.delete(COMPANY_ID, CompanyMemberRole.MEMBER, 8L, PROF_ID, 55L));
        assertEquals(403, ex.getStatusCode().value());
        verify(noteRepository, never()).delete(any());
    }

    @Test
    void delete_ownerDeletesAnothersNote() {
        CompanyCandidateNote n = note(55L, memberA, "a@acme.com");
        when(noteRepository.findById(55L)).thenReturn(Optional.of(n));

        service.delete(COMPANY_ID, CompanyMemberRole.OWNER, 9L, PROF_ID, 55L);

        verify(noteRepository).delete(n);
    }

    @Test
    void delete_noteNotFound_404() {
        when(noteRepository.findById(55L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> service.delete(COMPANY_ID, CompanyMemberRole.OWNER, 9L, PROF_ID, 55L));
    }
}
