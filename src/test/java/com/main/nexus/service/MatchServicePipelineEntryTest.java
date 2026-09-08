package com.main.nexus.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.InterestStatus;
import com.main.nexus.model.enums.ProjectStatus;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.MatchRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// Cobre a fiação da auto-entrada no Kanban: quando MatchService leva um match para
// COMPANY_INTERESTED/PROFESSIONAL_INTERESTED, PipelineService.assignInitialStageIfAbsent é
// chamado. A lógica de qual etapa/seed fica em PipelineServiceTest.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchServicePipelineEntryTest {

    @Mock private MatchRepository matchRepository;
    @Mock private MatchHistoryService matchHistoryService;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;
    @Mock private PipelineService pipelineService;

    @InjectMocks private MatchService matchService;

    private Match match;

    @BeforeEach
    void setUp() {
        Company company = new Company();
        company.setId(10L);
        company.setCompanyName("Acme");

        Project project = new Project();
        project.setId(100L);
        project.setCompany(company);
        project.setTitle("Vaga X");
        project.setStatus(ProjectStatus.OPEN);

        User profUser = new User();
        profUser.setId(2L);
        profUser.setEmail("ana@dev.com");
        Professional professional = new Professional();
        professional.setId(1L);
        professional.setName("Ana");
        professional.setUser(profUser);

        match = new Match();
        match.setId(1L);
        match.setProject(project);
        match.setProfessional(professional);
        match.setStatus(StatusMatch.WAITING);
        match.setCompanyStatus(InterestStatus.PENDING);
        match.setProfessionalStatus(InterestStatus.PENDING);

        when(matchRepository.findById(1L)).thenReturn(Optional.of(match));
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void companyShowsInterest_entersBoardOnCompanyInterested() {
        matchService.companyShowsInterest(1L, 10L);

        // sanity: a transição aconteceu
        org.junit.jupiter.api.Assertions.assertEquals(
                StatusMatch.COMPANY_INTERESTED, match.getStatus());
        verify(pipelineService).assignInitialStageIfAbsent(match);
    }

    @Test
    void companyShowsInterest_wrongCompany_doesNotEnterBoard() {
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> matchService.companyShowsInterest(1L, 999L));
        verify(pipelineService, never()).assignInitialStageIfAbsent(any());
    }
}
