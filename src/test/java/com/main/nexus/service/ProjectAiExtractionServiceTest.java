package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.main.nexus.dto.AiExtractionResponseDTO;
import com.main.nexus.dto.AiOpportunityExtractionDTO;
import com.main.nexus.dto.AiSkillSuggestionDTO;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.ContractType;
import com.main.nexus.model.enums.Modality;
import com.main.nexus.model.enums.OpportunityType;
import com.main.nexus.model.enums.ProjectType;
import com.main.nexus.ratelimit.CaffeineRateLimitStore;
import com.main.nexus.repository.SkillRepository;
import com.main.nexus.service.ai.FakeAiExtractionClient;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProjectAiExtractionServiceTest {

    @Mock
    private SkillRepository skillRepository;

    private FakeAiExtractionClient fakeClient;
    private ProjectAiExtractionService service;

    @BeforeEach
    void setUp() {
        fakeClient = new FakeAiExtractionClient();
        service = new ProjectAiExtractionService();
        ReflectionTestUtils.setField(service, "aiExtractionClient", fakeClient);
        ReflectionTestUtils.setField(service, "skillRepository", skillRepository);
        // Store real em memoria (sem Spring) -- a cota de 10/h continua sendo
        // exercitada de verdade por enforcesRateLimitPerCompany.
        ReflectionTestUtils.setField(service, "rateLimitStore", new CaffeineRateLimitStore());
    }

    private Skill skill(Long id, String name) {
        Skill s = new Skill();
        s.setId(id);
        s.setName(name);
        return s;
    }

    private AiOpportunityExtractionDTO emptySuggestion() {
        return new AiOpportunityExtractionDTO(
                null, null, null, null, null, null, null,
                null, null, null,
                null, null, null, List.of(), null, null,
                null, List.of(), List.of());
    }

    @Test
    void rejectsTextTooShort() {
        assertThrows(ResponseStatusException.class, () -> service.extract(1L, "oi"));
    }

    @Test
    void rejectsTextTooLong() {
        String huge = "a".repeat(6001);
        assertThrows(ResponseStatusException.class, () -> service.extract(1L, huge));
    }

    @Test
    void normalizesProjectSuggestionAndMatchesSkills() {
        when(skillRepository.findAllByActiveTrue()).thenReturn(
                List.of(skill(1L, "React"), skill(2L, "Node.js")));

        AiOpportunityExtractionDTO suggestion = new AiOpportunityExtractionDTO(
                "Título", "Descrição", OpportunityType.PROJECT, ProjectType.FREELANCE,
                Modality.REMOTE, null, null,
                8000.0, 15000.0, LocalDate.now().plusDays(30),
                null, null, null, List.of(), null, null,
                null,
                List.of(new AiSkillSuggestionDTO("React", null, null, false)),
                List.of(new AiSkillSuggestionDTO("Cobol", null, null, false)));

        fakeClient.willReturn(new AiExtractionResponseDTO(suggestion, List.of()));

        AiExtractionResponseDTO result = service.extract(1L, "texto valido com mais de vinte caracteres");

        assertEquals(OpportunityType.PROJECT, result.suggestion().opportunityType());
        assertEquals(8000.0, result.suggestion().minimumBudget());
        assertTrue(result.suggestion().requiredSkills().get(0).foundInCatalog());
        assertEquals(1L, result.suggestion().requiredSkills().get(0).matchedSkillId());
        assertFalse(result.suggestion().niceToHaveSkills().get(0).foundInCatalog());
    }

    @Test
    void neverKeepsJobFieldsWhenOpportunityTypeIsProject() {
        AiOpportunityExtractionDTO suggestion = new AiOpportunityExtractionDTO(
                "Título", "Descrição", OpportunityType.PROJECT, null, null, null, null,
                5000.0, 9000.0, LocalDate.now().plusDays(10),
                ContractType.CLT, 3000.0, 4000.0, List.of("Vale-refeição"),
                LocalDate.now(), 40,
                null, List.of(), List.of());

        fakeClient.willReturn(new AiExtractionResponseDTO(suggestion, List.of()));

        AiExtractionResponseDTO result = service.extract(1L, "texto valido com mais de vinte caracteres");

        assertNull(result.suggestion().contractType());
        assertNull(result.suggestion().monthlySalaryMin());
        assertNull(result.suggestion().monthlySalaryMax());
        assertNull(result.suggestion().startDate());
        assertNull(result.suggestion().workloadHoursPerWeek());
        assertTrue(result.suggestion().benefits().isEmpty());
    }

    @Test
    void discardsInvertedBudgetRangeAndFlagsLowConfidence() {
        AiOpportunityExtractionDTO suggestion = new AiOpportunityExtractionDTO(
                null, null, OpportunityType.PROJECT, null, null, null, null,
                20000.0, 5000.0, null,
                null, null, null, List.of(), null, null,
                null, List.of(), List.of());

        fakeClient.willReturn(new AiExtractionResponseDTO(suggestion, List.of()));

        AiExtractionResponseDTO result = service.extract(1L, "texto valido com mais de vinte caracteres");

        assertNull(result.suggestion().minimumBudget());
        assertNull(result.suggestion().maximumBudget());
        assertTrue(result.lowConfidenceFields().contains("minimumBudget"));
    }

    @Test
    void discardsPastDeadline() {
        AiOpportunityExtractionDTO suggestion = new AiOpportunityExtractionDTO(
                null, null, OpportunityType.PROJECT, null, null, null, null,
                1000.0, 2000.0, LocalDate.now().minusDays(1),
                null, null, null, List.of(), null, null,
                null, List.of(), List.of());

        fakeClient.willReturn(new AiExtractionResponseDTO(suggestion, List.of()));

        AiExtractionResponseDTO result = service.extract(1L, "texto valido com mais de vinte caracteres");

        assertNull(result.suggestion().deadline());
        assertTrue(result.lowConfidenceFields().contains("deadline"));
    }

    @Test
    void discardsCepWithInvalidFormat() {
        AiOpportunityExtractionDTO suggestion = new AiOpportunityExtractionDTO(
                null, null, null, null, null, null, null,
                null, null, null,
                null, null, null, List.of(), null, null,
                "abc-123", List.of(), List.of());

        fakeClient.willReturn(new AiExtractionResponseDTO(suggestion, List.of()));

        AiExtractionResponseDTO result = service.extract(1L, "texto valido com mais de vinte caracteres");

        assertNull(result.suggestion().cep());
    }

    @Test
    void handlesNullSuggestionFromClient() {
        fakeClient.willReturn(new AiExtractionResponseDTO(null, null));

        AiExtractionResponseDTO result = service.extract(1L, "texto valido com mais de vinte caracteres");

        assertNull(result.suggestion().opportunityType());
        assertTrue(result.lowConfidenceFields().isEmpty());
    }

    @Test
    void propagatesClientFailureAsResponseStatusException() {
        fakeClient.willThrow(new ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(502), "fora do ar"));

        assertThrows(ResponseStatusException.class,
                () -> service.extract(1L, "texto valido com mais de vinte caracteres"));
    }

    @Test
    void enforcesRateLimitPerCompany() {
        fakeClient.willReturn(new AiExtractionResponseDTO(emptySuggestion(), List.of()));

        for (int i = 0; i < 10; i++) {
            service.extract(1L, "texto valido com mais de vinte caracteres");
        }

        assertThrows(ResponseStatusException.class,
                () -> service.extract(1L, "texto valido com mais de vinte caracteres"));
    }
}
