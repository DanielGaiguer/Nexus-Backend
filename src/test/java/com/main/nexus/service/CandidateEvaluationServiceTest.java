package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.main.nexus.dto.CandidateEvaluationItemDTO;
import com.main.nexus.dto.CandidateEvaluationRequestDTO;
import com.main.nexus.dto.CandidateEvaluationSummaryDTO;
import com.main.nexus.model.CandidateEvaluation;
import com.main.nexus.model.Company;
import com.main.nexus.model.CompanyMember;
import com.main.nexus.model.Match;
import com.main.nexus.model.Project;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.CompanyMemberRole;
import com.main.nexus.model.enums.CompanyMemberStatus;
import com.main.nexus.repository.CandidateEvaluationRepository;
import com.main.nexus.repository.CompanyMemberRepository;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.ProjectRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
class CandidateEvaluationServiceTest {

    private static final Long COMPANY_ID = 10L;
    private static final Long PROJECT_ID = 100L;
    private static final Long MATCH_ID = 500L;

    @Mock private CandidateEvaluationRepository evaluationRepository;
    @Mock private CompanyMemberRepository companyMemberRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private ProjectRepository projectRepository;

    @InjectMocks private CandidateEvaluationService service;

    private Company company;
    private Project project;
    private Match match;
    private CompanyMember memberA; // user 7, MEMBER, id 100
    private CompanyMember memberB; // user 8, MEMBER, id 200

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(COMPANY_ID);

        project = new Project();
        project.setId(PROJECT_ID);
        project.setCompany(company);

        match = new Match();
        match.setId(MATCH_ID);
        match.setProject(project);

        memberA = member(100L, 7L, "a@acme.com");
        memberB = member(200L, 8L, "b@acme.com");

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
        when(companyMemberRepository.findByUserIdAndStatus(7L, CompanyMemberStatus.ACTIVE))
                .thenReturn(Optional.of(memberA));
        when(companyMemberRepository.findByUserIdAndStatus(8L, CompanyMemberStatus.ACTIVE))
                .thenReturn(Optional.of(memberB));
        when(evaluationRepository.save(any(CandidateEvaluation.class))).thenAnswer(inv -> {
            CandidateEvaluation e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(999L);
            }
            return e;
        });
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private CompanyMember member(Long id, Long userId, String email) {
        User u = new User();
        u.setId(userId);
        u.setEmail(email);
        CompanyMember m = new CompanyMember();
        m.setId(id);
        m.setCompany(company);
        m.setUser(u);
        m.setRole(CompanyMemberRole.MEMBER);
        m.setStatus(CompanyMemberStatus.ACTIVE);
        return m;
    }

    private CandidateEvaluation evaluation(
            Long id, CompanyMember evaluator, String label, int rating, String comment) {
        CandidateEvaluation e = new CandidateEvaluation();
        e.setId(id);
        e.setMatch(match);
        e.setEvaluator(evaluator);
        e.setEvaluatorLabel(label);
        e.setRating(rating);
        e.setComment(comment);
        e.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        e.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        return e;
    }

    // ─── Upsert ─────────────────────────────────────────────────────

    @Test
    void upsertMine_createsWhenAbsent_snapshotsLabelAndTrimsComment() {
        when(evaluationRepository.findByMatchIdAndEvaluatorId(MATCH_ID, 100L))
                .thenReturn(Optional.empty());

        CandidateEvaluationItemDTO dto = service.upsertMine(PROJECT_ID, MATCH_ID, COMPANY_ID, 7L,
                new CandidateEvaluationRequestDTO(4, "  bom raciocínio  "));

        ArgumentCaptor<CandidateEvaluation> captor = ArgumentCaptor.forClass(CandidateEvaluation.class);
        verify(evaluationRepository).save(captor.capture());
        CandidateEvaluation saved = captor.getValue();
        assertEquals(4, saved.getRating());
        assertEquals("bom raciocínio", saved.getComment());
        assertEquals("a@acme.com", saved.getEvaluatorLabel());
        assertSame(memberA, saved.getEvaluator());
        assertSame(match, saved.getMatch());

        assertTrue(dto.mine());
        assertEquals(4, dto.rating());
        assertEquals("a@acme.com", dto.evaluatorLabel());
    }

    @Test
    void upsertMine_updatesOwnEvaluation_keepsLabelSnapshotAndCreatedAt() {
        CandidateEvaluation existing = evaluation(55L, memberA, "a@acme.com", 3, "meh");
        LocalDateTime originalCreatedAt = existing.getCreatedAt();
        when(evaluationRepository.findByMatchIdAndEvaluatorId(MATCH_ID, 100L))
                .thenReturn(Optional.of(existing));

        service.upsertMine(PROJECT_ID, MATCH_ID, COMPANY_ID, 7L,
                new CandidateEvaluationRequestDTO(5, "mudei de ideia"));

        assertEquals(5, existing.getRating());
        assertEquals("mudei de ideia", existing.getComment());
        assertEquals("a@acme.com", existing.getEvaluatorLabel()); // snapshot inalterado
        assertEquals(originalCreatedAt, existing.getCreatedAt());  // createdAt inalterado
        verify(evaluationRepository).save(existing);
    }

    @Test
    void upsertMine_ratingBelowRange_rejected() {
        assertThrows(ResponseStatusException.class, () -> service.upsertMine(
                PROJECT_ID, MATCH_ID, COMPANY_ID, 7L, new CandidateEvaluationRequestDTO(0, null)));
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void upsertMine_ratingAboveRange_rejected() {
        assertThrows(ResponseStatusException.class, () -> service.upsertMine(
                PROJECT_ID, MATCH_ID, COMPANY_ID, 7L, new CandidateEvaluationRequestDTO(6, null)));
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void upsertMine_ratingNull_rejected() {
        assertThrows(ResponseStatusException.class, () -> service.upsertMine(
                PROJECT_ID, MATCH_ID, COMPANY_ID, 7L, new CandidateEvaluationRequestDTO(null, "x")));
    }

    @Test
    void upsertMine_blankCommentStoredAsNull() {
        when(evaluationRepository.findByMatchIdAndEvaluatorId(MATCH_ID, 100L))
                .thenReturn(Optional.empty());

        service.upsertMine(PROJECT_ID, MATCH_ID, COMPANY_ID, 7L,
                new CandidateEvaluationRequestDTO(3, "   "));

        ArgumentCaptor<CandidateEvaluation> captor = ArgumentCaptor.forClass(CandidateEvaluation.class);
        verify(evaluationRepository).save(captor.capture());
        assertNull(captor.getValue().getComment());
    }

    @Test
    void upsertMine_notActiveMember_403() {
        when(companyMemberRepository.findByUserIdAndStatus(7L, CompanyMemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.upsertMine(PROJECT_ID, MATCH_ID, COMPANY_ID, 7L,
                        new CandidateEvaluationRequestDTO(4, null)));
        assertEquals(403, ex.getStatusCode().value());
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void upsertMine_projectFromAnotherCompany_403() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.upsertMine(PROJECT_ID, MATCH_ID, 999L, 7L,
                        new CandidateEvaluationRequestDTO(4, null)));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void upsertMine_matchNotInProject_400() {
        Project other = new Project();
        other.setId(777L);
        other.setCompany(company);
        match.setProject(other);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.upsertMine(PROJECT_ID, MATCH_ID, COMPANY_ID, 7L,
                        new CandidateEvaluationRequestDTO(4, null)));
        assertEquals(400, ex.getStatusCode().value());
    }

    // ─── Consolidado ────────────────────────────────────────────────

    @Test
    void getSummary_multipleEvaluators_averageCountAndItems() {
        CandidateEvaluation e1 = evaluation(1L, memberA, "a@acme.com", 4, "sólido");
        CandidateEvaluation e2 = evaluation(2L, memberB, "b@acme.com", 5, null);
        CandidateEvaluation orphan = evaluation(3L, null, "ex-membro@acme.com", 3, "antigo");
        when(evaluationRepository.findByMatchIdOrderByCreatedAtAsc(MATCH_ID))
                .thenReturn(List.of(e1, e2, orphan));

        CandidateEvaluationSummaryDTO summary =
                service.getSummary(PROJECT_ID, MATCH_ID, COMPANY_ID, 7L);

        assertEquals(4.0, summary.average());
        assertEquals(3, summary.count());
        assertEquals(3, summary.items().size());
        assertTrue(summary.items().get(0).mine());          // e1 é do viewer (memberA)
        assertFalse(summary.items().get(1).mine());
        assertEquals("b@acme.com", summary.items().get(1).evaluatorLabel());
        assertNull(summary.items().get(2).evaluatorMemberId()); // órfã
        assertFalse(summary.items().get(2).mine());
    }

    @Test
    void getSummary_noEvaluations_nullAverageZeroCount() {
        when(evaluationRepository.findByMatchIdOrderByCreatedAtAsc(MATCH_ID))
                .thenReturn(List.of());

        CandidateEvaluationSummaryDTO summary =
                service.getSummary(PROJECT_ID, MATCH_ID, COMPANY_ID, 7L);

        assertNull(summary.average());
        assertEquals(0, summary.count());
        assertTrue(summary.items().isEmpty());
    }

    @Test
    void getSummary_averageRoundedToOneDecimal() {
        when(evaluationRepository.findByMatchIdOrderByCreatedAtAsc(MATCH_ID)).thenReturn(List.of(
                evaluation(1L, memberA, "a@acme.com", 5, null),
                evaluation(2L, memberB, "b@acme.com", 4, null),
                evaluation(3L, null, "x@acme.com", 4, null)));

        CandidateEvaluationSummaryDTO summary =
                service.getSummary(PROJECT_ID, MATCH_ID, COMPANY_ID, 7L);

        assertEquals(4.3, summary.average()); // 13/3 = 4.333... -> 4.3
    }

    @Test
    void getSummary_projectFromAnotherCompany_403() {
        assertThrows(ResponseStatusException.class,
                () -> service.getSummary(PROJECT_ID, MATCH_ID, 999L, 7L));
    }

    // ─── Agregação para o board ─────────────────────────────────────

    @Test
    void aggregate_mapsRowsAndRounds() {
        when(evaluationRepository.aggregateByMatchIds(any())).thenReturn(List.of(
                new Object[] {500L, 4.3333d, 3L},
                new Object[] {501L, 5.0d, 1L}));

        Map<Long, double[]> result = service.aggregate(List.of(500L, 501L));

        assertEquals(4.3, result.get(500L)[0]);
        assertEquals(3.0, result.get(500L)[1]);
        assertEquals(5.0, result.get(501L)[0]);
        assertEquals(1.0, result.get(501L)[1]);
    }

    @Test
    void aggregate_emptyInput_returnsEmptyMapWithoutQuery() {
        Map<Long, double[]> result = service.aggregate(List.of());
        assertTrue(result.isEmpty());
        verify(evaluationRepository, never()).aggregateByMatchIds(any());
    }
}
