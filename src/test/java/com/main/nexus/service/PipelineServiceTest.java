package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.main.nexus.dto.PipelineBoardDTO;
import com.main.nexus.dto.PipelineCardDTO;
import com.main.nexus.dto.PipelineStageDTO;
import com.main.nexus.dto.PipelineStageRequestDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.CompanyMember;
import com.main.nexus.model.Match;
import com.main.nexus.model.MatchConfirmation;
import com.main.nexus.model.MatchHistory;
import com.main.nexus.model.PipelineStage;
import com.main.nexus.model.PipelineStageHistory;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.Proposal;
import com.main.nexus.model.RejectionFeedback;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.CompanyMemberStatus;
import com.main.nexus.model.enums.InterestStatus;
import com.main.nexus.model.enums.MatchConfirmationStatus;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.CompanyMemberRepository;
import com.main.nexus.repository.MatchConfirmationRepository;
import com.main.nexus.repository.MatchHistoryRepository;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.PipelineStageHistoryRepository;
import com.main.nexus.repository.PipelineStageRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.RejectionFeedbackRepository;
import java.util.ArrayList;
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
class PipelineServiceTest {

    private static final Long COMPANY_ID = 10L;
    private static final Long PROJECT_ID = 100L;
    private static final Long MATCH_ID = 500L;
    private static final Long USER_ID = 7L;

    @Mock private PipelineStageRepository pipelineStageRepository;
    @Mock private PipelineStageHistoryRepository pipelineStageHistoryRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private CompanyMemberRepository companyMemberRepository;
    @Mock private RejectionFeedbackRepository rejectionFeedbackRepository;
    @Mock private MatchConfirmationRepository matchConfirmationRepository;
    @Mock private MatchHistoryRepository matchHistoryRepository;
    @Mock private ScreeningInvitationService screeningInvitationService;
    @Mock private CandidateEvaluationService candidateEvaluationService;

    @InjectMocks private PipelineService service;

    private Company company;
    private Project project;
    private Professional professional;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(COMPANY_ID);

        project = new Project();
        project.setId(PROJECT_ID);
        project.setCompany(company);

        professional = new Professional();
        professional.setId(1L);
        professional.setName("Ana Dev");

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(screeningInvitationService.getSummariesFor(any(), any())).thenReturn(List.of());
        // Board tests: por padrão a vaga já tem etapas (não semeia) e nenhum feedback/confirmação.
        when(pipelineStageRepository.countByProjectId(PROJECT_ID)).thenReturn(5L);
        when(rejectionFeedbackRepository.findByMatchId(anyLong())).thenReturn(Optional.empty());
        when(matchConfirmationRepository.findByMatchId(anyLong())).thenReturn(Optional.empty());
        when(matchHistoryRepository.findByMatchIdOrderByChangedAtAsc(anyLong())).thenReturn(List.of());
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private PipelineStage stage(Long id, int orderIndex, boolean active, String name) {
        PipelineStage s = new PipelineStage();
        s.setId(id);
        s.setProject(project);
        s.setOrderIndex(orderIndex);
        s.setActive(active);
        s.setName(name);
        return s;
    }

    private Match match(StatusMatch status, boolean active) {
        Match m = new Match();
        m.setId(MATCH_ID);
        m.setProject(project);
        m.setProfessional(professional);
        m.setStatus(status);
        m.setActive(active);
        m.setMatchScore(80.0);
        return m;
    }

    private CompanyMember activeMember() {
        User u = new User();
        u.setId(USER_ID);
        u.setEmail("recruiter@acme.com");
        CompanyMember cm = new CompanyMember();
        cm.setId(1L);
        cm.setCompany(company);
        cm.setUser(u);
        cm.setStatus(CompanyMemberStatus.ACTIVE);
        return cm;
    }

    /** Monta o board com um único match e devolve o card resolvido. */
    private PipelineCardDTO cardOf(Match m) {
        when(matchRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(m));
        when(pipelineStageRepository.findByProjectIdOrderByOrderIndexAsc(PROJECT_ID))
                .thenReturn(List.of(stage(1L, 0, true, "Triagem")));
        PipelineBoardDTO board = service.getBoard(PROJECT_ID, COMPANY_ID);
        return board.cards().isEmpty() ? null : board.cards().get(0);
    }

    // ─── Seed lazy das 5 etapas default ─────────────────────────────

    @Test
    void getStages_seedsFiveDefaultsWhenProjectHasNone() {
        when(pipelineStageRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);
        List<PipelineStage> seeded = List.of(
                stage(1L, 0, true, "Triagem"),
                stage(2L, 1, true, "Teste"),
                stage(3L, 2, true, "Entrevista"),
                stage(4L, 3, true, "Dinâmica"),
                stage(5L, 4, true, "Proposta"));
        when(pipelineStageRepository.findByProjectIdOrderByOrderIndexAsc(PROJECT_ID)).thenReturn(seeded);

        List<PipelineStageDTO> result = service.getStages(PROJECT_ID, COMPANY_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PipelineStage>> captor = ArgumentCaptor.forClass(List.class);
        verify(pipelineStageRepository).saveAll(captor.capture());
        List<PipelineStage> saved = captor.getValue();
        assertEquals(5, saved.size());
        assertEquals(List.of("Triagem", "Teste", "Entrevista", "Dinâmica", "Proposta"),
                saved.stream().map(PipelineStage::getName).toList());
        assertEquals(List.of(0, 1, 2, 3, 4),
                saved.stream().map(PipelineStage::getOrderIndex).toList());
        assertTrue(saved.stream().allMatch(PipelineStage::getActive));
        assertEquals(5, result.size());
        assertEquals("Triagem", result.get(0).name());
    }

    @Test
    void getStages_doesNotReseedWhenStagesExist_evenIfAllInactive() {
        when(pipelineStageRepository.countByProjectId(PROJECT_ID)).thenReturn(3L);
        when(pipelineStageRepository.findByProjectIdOrderByOrderIndexAsc(PROJECT_ID))
                .thenReturn(List.of(stage(1L, 0, false, "Velha")));

        service.getStages(PROJECT_ID, COMPANY_ID);

        verify(pipelineStageRepository, never()).saveAll(any());
    }

    @Test
    void getStages_403WhenProjectFromAnotherCompany() {
        assertThrows(ResponseStatusException.class,
                () -> service.getStages(PROJECT_ID, 999L));
    }

    // ─── Auto-entrada no board ──────────────────────────────────────

    @Test
    void assignInitialStageIfAbsent_assignsFirstActiveStage() {
        Match m = match(StatusMatch.COMPANY_INTERESTED, true);
        PipelineStage triagem = stage(1L, 0, true, "Triagem");
        when(pipelineStageRepository.findFirstByProjectIdAndActiveTrueOrderByOrderIndexAsc(PROJECT_ID))
                .thenReturn(Optional.of(triagem));

        service.assignInitialStageIfAbsent(m);

        assertSame(triagem, m.getPipelineStage());
        verify(matchRepository).save(m);
    }

    @Test
    void assignInitialStageIfAbsent_seedsDefaultsThenAssigns_whenProjectHasNoStages() {
        Match m = match(StatusMatch.PROFESSIONAL_INTERESTED, true);
        PipelineStage triagem = stage(1L, 0, true, "Triagem");
        when(pipelineStageRepository.findFirstByProjectIdAndActiveTrueOrderByOrderIndexAsc(PROJECT_ID))
                .thenReturn(Optional.empty(), Optional.of(triagem));
        when(pipelineStageRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);

        service.assignInitialStageIfAbsent(m);

        verify(pipelineStageRepository).saveAll(any());
        assertSame(triagem, m.getPipelineStage());
    }

    @Test
    void assignInitialStageIfAbsent_noOpWhenCardAlreadyOnBoard() {
        Match m = match(StatusMatch.COMPANY_INTERESTED, true);
        m.setPipelineStage(stage(9L, 2, true, "Entrevista"));

        service.assignInitialStageIfAbsent(m);

        verify(pipelineStageRepository, never())
                .findFirstByProjectIdAndActiveTrueOrderByOrderIndexAsc(anyLong());
        verify(matchRepository, never()).save(any());
    }

    // ─── Mover card entre etapas ────────────────────────────────────

    @Test
    void moveCard_movesBetweenActiveStages_andWritesHistory() {
        Match m = match(StatusMatch.COMPANY_INTERESTED, true);
        PipelineStage from = stage(1L, 0, true, "Triagem");
        PipelineStage to = stage(2L, 1, true, "Entrevista");
        m.setPipelineStage(from);
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
        when(pipelineStageRepository.findById(2L)).thenReturn(Optional.of(to));
        when(companyMemberRepository.findByUserIdAndStatus(USER_ID, CompanyMemberStatus.ACTIVE))
                .thenReturn(Optional.of(activeMember()));

        PipelineCardDTO card = service.moveCard(PROJECT_ID, MATCH_ID, 2L, COMPANY_ID, USER_ID);

        assertSame(to, m.getPipelineStage());
        verify(matchRepository).save(m);
        ArgumentCaptor<PipelineStageHistory> captor = ArgumentCaptor.forClass(PipelineStageHistory.class);
        verify(pipelineStageHistoryRepository).save(captor.capture());
        PipelineStageHistory row = captor.getValue();
        assertSame(from, row.getFromStage());
        assertSame(to, row.getToStage());
        assertEquals("recruiter@acme.com", row.getMovedBy().getUser().getEmail());
        assertTrue(row.getMovedAt() != null);
        assertEquals("STAGE", card.column().kind());
        assertEquals(Long.valueOf(2), card.column().stageId());
        assertTrue(card.draggable());
    }

    @Test
    void moveCard_400WhenTargetStageInactive() {
        Match m = match(StatusMatch.COMPANY_INTERESTED, true);
        m.setPipelineStage(stage(1L, 0, true, "Triagem"));
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
        when(pipelineStageRepository.findById(2L))
                .thenReturn(Optional.of(stage(2L, 1, false, "Arquivada")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.moveCard(PROJECT_ID, MATCH_ID, 2L, COMPANY_ID, USER_ID));
        assertTrue(ex.getReason().toLowerCase().contains("archived"));
        verify(matchRepository, never()).save(any());
        verify(pipelineStageHistoryRepository, never()).save(any());
    }

    @Test
    void moveCard_400WhenTargetStageFromAnotherProject() {
        Match m = match(StatusMatch.COMPANY_INTERESTED, true);
        m.setPipelineStage(stage(1L, 0, true, "Triagem"));
        Project other = new Project();
        other.setId(777L);
        other.setCompany(company);
        PipelineStage alien = new PipelineStage();
        alien.setId(2L);
        alien.setProject(other);
        alien.setActive(true);
        alien.setName("De outra vaga");
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
        when(pipelineStageRepository.findById(2L)).thenReturn(Optional.of(alien));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.moveCard(PROJECT_ID, MATCH_ID, 2L, COMPANY_ID, USER_ID));
        assertTrue(ex.getReason().toLowerCase().contains("another project"));
        verify(pipelineStageHistoryRepository, never()).save(any());
    }

    @Test
    void moveCard_400WhenCardInTerminalColumn() {
        Match m = match(StatusMatch.MATCHED, true); // deriveColumn -> HIRED
        m.setPipelineStage(stage(1L, 0, true, "Triagem"));
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
        when(pipelineStageRepository.findById(2L)).thenReturn(Optional.of(stage(2L, 1, true, "Entrevista")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.moveCard(PROJECT_ID, MATCH_ID, 2L, COMPANY_ID, USER_ID));
        assertTrue(ex.getReason().toLowerCase().contains("terminal"));
        verify(matchRepository, never()).save(any());
        verify(pipelineStageHistoryRepository, never()).save(any());
    }

    @Test
    void moveCard_neverTouchesStatusRejectionOrConfirmation() {
        Match m = match(StatusMatch.PROFESSIONAL_INTERESTED, true);
        PipelineStage from = stage(1L, 0, true, "Triagem");
        m.setPipelineStage(from);
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
        when(pipelineStageRepository.findById(2L)).thenReturn(Optional.of(stage(2L, 1, true, "Teste")));
        when(companyMemberRepository.findByUserIdAndStatus(USER_ID, CompanyMemberStatus.ACTIVE))
                .thenReturn(Optional.of(activeMember()));

        service.moveCard(PROJECT_ID, MATCH_ID, 2L, COMPANY_ID, USER_ID);

        assertEquals(StatusMatch.PROFESSIONAL_INTERESTED, m.getStatus());
        assertEquals(InterestStatus.PENDING, m.getCompanyStatus());
        verify(rejectionFeedbackRepository, never()).save(any());
        verify(matchConfirmationRepository, never()).save(any());
    }

    @Test
    void moveCard_noOpWhenAlreadyInTargetStage() {
        Match m = match(StatusMatch.COMPANY_INTERESTED, true);
        PipelineStage current = stage(2L, 1, true, "Entrevista");
        m.setPipelineStage(current);
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
        when(pipelineStageRepository.findById(2L)).thenReturn(Optional.of(current));

        service.moveCard(PROJECT_ID, MATCH_ID, 2L, COMPANY_ID, USER_ID);

        verify(pipelineStageHistoryRepository, never()).save(any());
        verify(matchRepository, never()).save(any());
    }

    @Test
    void moveCard_404WhenMatchMissing() {
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> service.moveCard(PROJECT_ID, MATCH_ID, 2L, COMPANY_ID, USER_ID));
    }

    @Test
    void moveCard_400WhenMatchNotInProject() {
        Match m = match(StatusMatch.COMPANY_INTERESTED, true);
        Project other = new Project();
        other.setId(888L);
        other.setCompany(company);
        m.setProject(other);
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));

        assertThrows(ResponseStatusException.class,
                () -> service.moveCard(PROJECT_ID, MATCH_ID, 2L, COMPANY_ID, USER_ID));
    }

    // ─── Derivação da coluna (tabela do relatório) ──────────────────

    @Test
    void board_hired_matchConfirmed() {
        PipelineCardDTO card = cardOf(match(StatusMatch.MATCHED, true));
        assertEquals("HIRED", card.column().kind());
        assertEquals("Match confirmado", card.column().subLabel());
        assertFalse(card.draggable());
    }

    @Test
    void board_hired_proposalAccepted() {
        Match m = match(StatusMatch.MATCHED, true);
        m.setAcceptedProposal(new Proposal());
        PipelineCardDTO card = cardOf(m);
        assertEquals("HIRED", card.column().kind());
        assertEquals("Proposta aceita", card.column().subLabel());
    }

    @Test
    void board_hired_confirmedWindow() {
        MatchConfirmation confirmation = new MatchConfirmation();
        confirmation.setStatus(MatchConfirmationStatus.CONFIRMED);
        when(matchConfirmationRepository.findByMatchId(MATCH_ID)).thenReturn(Optional.of(confirmation));

        PipelineCardDTO card = cardOf(match(StatusMatch.MATCHED, true));
        assertEquals("HIRED", card.column().kind());
        assertEquals("Confirmada ✓", card.column().subLabel());
    }

    @Test
    void board_hired_expiredWithoutConfirmation() {
        PipelineCardDTO card = cardOf(match(StatusMatch.MATCHED, false));
        assertEquals("HIRED", card.column().kind());
        assertEquals("Match expirado — sem confirmação", card.column().subLabel());
    }

    @Test
    void board_rejected_byCompany() {
        RejectionFeedback rf = new RejectionFeedback();
        rf.setRejectedBy(AuthorType.COMPANY);
        when(rejectionFeedbackRepository.findByMatchId(MATCH_ID)).thenReturn(Optional.of(rf));

        PipelineCardDTO card = cardOf(match(StatusMatch.REJECTED, true));
        assertEquals("REJECTED", card.column().kind());
        assertEquals("Recusado pela empresa", card.column().subLabel());
        assertFalse(card.draggable());
    }

    @Test
    void board_rejected_byProfessional() {
        RejectionFeedback rf = new RejectionFeedback();
        rf.setRejectedBy(AuthorType.PROFESSIONAL);
        when(rejectionFeedbackRepository.findByMatchId(MATCH_ID)).thenReturn(Optional.of(rf));

        PipelineCardDTO card = cardOf(match(StatusMatch.REJECTED, true));
        assertEquals("REJECTED", card.column().kind());
        assertEquals("Candidato recusou", card.column().subLabel());
    }

    @Test
    void board_rejected_professionalWithdrew() {
        Match m = match(StatusMatch.REJECTED, false);
        m.setProfessionalStatus(InterestStatus.REJECTED);
        PipelineCardDTO card = cardOf(m);
        assertEquals("REJECTED", card.column().kind());
        assertEquals("Candidato retirou o interesse", card.column().subLabel());
    }

    @Test
    void board_rejected_matchCancelledByCompany() {
        Match m = match(StatusMatch.REJECTED, false);
        m.setCompanyStatus(InterestStatus.REJECTED);
        PipelineCardDTO card = cardOf(m);
        assertEquals("REJECTED", card.column().kind());
        assertEquals("Match cancelado", card.column().subLabel());
    }

    @Test
    void board_rejected_vagaEncerradaBySystem() {
        MatchHistory h = new MatchHistory();
        h.setChangedBy("SYSTEM");
        h.setFromStatus("COMPANY_INTERESTED");
        h.setToStatus("REJECTED");
        when(matchHistoryRepository.findByMatchIdOrderByChangedAtAsc(MATCH_ID)).thenReturn(List.of(h));

        PipelineCardDTO card = cardOf(match(StatusMatch.REJECTED, false));
        assertEquals("REJECTED", card.column().kind());
        assertEquals("Vaga encerrada", card.column().subLabel());
    }

    @Test
    void board_stageColumn_forInterestedMatch() {
        Match m = match(StatusMatch.COMPANY_INTERESTED, true);
        m.setPipelineStage(stage(3L, 2, true, "Entrevista"));
        PipelineCardDTO card = cardOf(m);
        assertEquals("STAGE", card.column().kind());
        assertEquals(Long.valueOf(3), card.column().stageId());
        assertNull(card.column().subLabel());
        assertTrue(card.draggable());
    }

    @Test
    void board_stageColumn_archivedStageStillShowsCard() {
        Match m = match(StatusMatch.COMPANY_INTERESTED, true);
        m.setPipelineStage(stage(4L, 3, false, "Dinâmica (arquivada)"));
        PipelineCardDTO card = cardOf(m);
        assertEquals("STAGE", card.column().kind());
        assertEquals("Etapa arquivada", card.column().subLabel());
        assertTrue(card.draggable()); // pode arrastar PRA FORA de uma etapa arquivada
    }

    @Test
    void board_cardCarriesScorecardSeparateFromMatchScore() {
        Match m = match(StatusMatch.COMPANY_INTERESTED, true);
        m.setPipelineStage(stage(1L, 0, true, "Triagem"));
        when(candidateEvaluationService.aggregate(any()))
                .thenReturn(java.util.Map.of(MATCH_ID, new double[] {4.5, 3}));

        PipelineCardDTO card = cardOf(m);

        assertEquals(80.0, card.matchScore());          // score algorítmico -- inalterado
        assertEquals(4.5, card.evaluation().average()); // scorecard humano -- campo separado
        assertEquals(3, card.evaluation().count());
    }

    @Test
    void board_cardWithNoEvaluationsHasZeroCountNullAverage() {
        Match m = match(StatusMatch.COMPANY_INTERESTED, true);
        m.setPipelineStage(stage(1L, 0, true, "Triagem"));
        PipelineCardDTO card = cardOf(m);
        assertNull(card.evaluation().average());
        assertEquals(0, card.evaluation().count());
    }

    @Test
    void board_excludesWaitingMatchWithoutStage() {
        Match m = match(StatusMatch.WAITING, true); // pipelineStage == null
        when(matchRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(m));
        when(pipelineStageRepository.findByProjectIdOrderByOrderIndexAsc(PROJECT_ID))
                .thenReturn(List.of(stage(1L, 0, true, "Triagem")));

        PipelineBoardDTO board = service.getBoard(PROJECT_ID, COMPANY_ID);

        assertTrue(board.cards().isEmpty());
        assertEquals(1, board.stages().size());
    }

    // ─── replaceStages (molde mergeStages) ─────────────────────────

    @Test
    void replaceStages_createReorderAndSoftDeleteOmittedWithCard() {
        PipelineStage s1 = stage(1L, 0, true, "A");
        PipelineStage s2 = stage(2L, 1, true, "B");
        when(pipelineStageRepository.findByProjectIdOrderByOrderIndexAsc(PROJECT_ID))
                .thenReturn(new ArrayList<>(List.of(s1, s2)), List.of(s2, stage(3L, 1, true, "C")));
        when(matchRepository.countByPipelineStageId(1L)).thenReturn(2L); // s1 tem card -> soft-delete

        service.replaceStages(PROJECT_ID, COMPANY_ID, List.of(
                new PipelineStageRequestDTO(2L, "B2"),
                new PipelineStageRequestDTO(null, "C")));

        assertFalse(s1.getActive());
        verify(pipelineStageRepository).save(s1);
        verify(pipelineStageRepository, never()).delete(s1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PipelineStage>> captor = ArgumentCaptor.forClass(List.class);
        verify(pipelineStageRepository).saveAll(captor.capture());
        List<PipelineStage> saved = captor.getValue();
        assertEquals("B2", saved.get(0).getName());
        assertEquals(0, saved.get(0).getOrderIndex());
        assertEquals("C", saved.get(1).getName());
        assertEquals(1, saved.get(1).getOrderIndex());
    }

    @Test
    void replaceStages_hardDeletesOmittedStageNeverUsed() {
        PipelineStage s1 = stage(1L, 0, true, "A");
        when(pipelineStageRepository.findByProjectIdOrderByOrderIndexAsc(PROJECT_ID))
                .thenReturn(new ArrayList<>(List.of(s1)), List.of(stage(2L, 0, true, "X")));
        when(matchRepository.countByPipelineStageId(1L)).thenReturn(0L);
        when(pipelineStageHistoryRepository.existsByStageReferenced(1L)).thenReturn(false);

        service.replaceStages(PROJECT_ID, COMPANY_ID,
                List.of(new PipelineStageRequestDTO(null, "X")));

        verify(pipelineStageRepository).delete(s1);
    }

    @Test
    void replaceStages_400WhenEmpty() {
        assertThrows(ResponseStatusException.class,
                () -> service.replaceStages(PROJECT_ID, COMPANY_ID, List.of()));
    }

    @Test
    void replaceStages_400WhenNameBlank() {
        when(pipelineStageRepository.findByProjectIdOrderByOrderIndexAsc(PROJECT_ID))
                .thenReturn(new ArrayList<>());
        assertThrows(ResponseStatusException.class,
                () -> service.replaceStages(PROJECT_ID, COMPANY_ID,
                        List.of(new PipelineStageRequestDTO(null, "  "))));
    }

    @Test
    void replaceStages_400WhenStageIdFromAnotherPipeline() {
        when(pipelineStageRepository.findByProjectIdOrderByOrderIndexAsc(PROJECT_ID))
                .thenReturn(new ArrayList<>(List.of(stage(1L, 0, true, "A"))));
        assertThrows(ResponseStatusException.class,
                () -> service.replaceStages(PROJECT_ID, COMPANY_ID,
                        List.of(new PipelineStageRequestDTO(999L, "Ghost"))));
    }
}
