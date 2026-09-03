package com.main.nexus.service;

import com.main.nexus.dto.AiExtractionResponseDTO;
import com.main.nexus.dto.AiOpportunityExtractionDTO;
import com.main.nexus.dto.AiSkillSuggestionDTO;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.OpportunityType;
import com.main.nexus.ratelimit.RateLimitExceededException;
import com.main.nexus.ratelimit.RateLimitPolicy;
import com.main.nexus.ratelimit.RateLimitStore;
import com.main.nexus.repository.SkillRepository;
import com.main.nexus.service.ai.AiExtractionClient;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Autopreenchimento de projeto/vaga via IA a partir de texto bruto. Só produz uma sugestão
// estruturada para pré-preencher o formulário de criação — não toca em ProjectRepository e não
// persiste nada. A validação final e definitiva continua sendo ProjectService.validateByType(),
// que roda exclusivamente quando a empresa de fato publica via POST /api/projects.
@Service
public class ProjectAiExtractionService {

    private static final int MIN_TEXT_LENGTH = 20;
    private static final int MAX_TEXT_LENGTH = 6000;

    @Autowired
    private AiExtractionClient aiExtractionClient;

    @Autowired
    private SkillRepository skillRepository;

    // Rate limit conservador por empresa — evita gasto descontrolado da cota gratuita
    // do Gemini. Antes era uma janela deslizante num Map local; agora reaproveita o
    // RateLimitStore da aplicação (impl Caffeine hoje, troca pontual por Redis se
    // escalar). A POLÍTICA continua sendo desta feature (AI_EXTRACT: 10/hora), não a
    // dos endpoints "caros" — é orçamento de cota, não pico de tráfego.
    @Autowired
    private RateLimitStore rateLimitStore;

    public AiExtractionResponseDTO extract(Long companyId, String rawText) {
        validateRawText(rawText);
        enforceRateLimit(companyId);

        AiExtractionResponseDTO raw = aiExtractionClient.extract(rawText.trim());

        return normalize(raw);
    }

    private void validateRawText(String rawText) {
        if (rawText == null || rawText.trim().length() < MIN_TEXT_LENGTH) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Texto muito curto para ser analisado. Cole a descrição completa da vaga.");
        }
        if (rawText.trim().length() > MAX_TEXT_LENGTH) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Texto muito longo (máximo de " + MAX_TEXT_LENGTH + " caracteres).");
        }
    }

    private void enforceRateLimit(Long companyId) {
        // Chave por empresa (== usuário hoje, User↔Company 1:1). Namespaced pela
        // política para não colidir com nenhum balde do RateLimitFilter.
        String key = RateLimitPolicy.AI_EXTRACT.name() + ":company:" + companyId;
        Bucket bucket = rateLimitStore.resolveBucket(key, RateLimitPolicy.AI_EXTRACT);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfter = Math.max(1, (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0));
            throw new RateLimitExceededException(
                    retryAfter,
                    RateLimitExceededException.REQUESTS,
                    "Limite de autopreenchimentos por IA atingido. Tente novamente mais tarde "
                    + "ou preencha o formulário manualmente.");
        }
    }

    // Normalização/validação defensiva pós-resposta. Não confia cegamente nas instruções do
    // prompt: revalida tudo que é barato/objetivo de revalidar aqui, mesmo sabendo que
    // ProjectService.validateByType() é a fonte de verdade final na criação real.
    
    // Esta é a parte que não confia no que o modelo disse, mesmo seguindo o prompt — é uma segunda camada de validação, independente e mais barata que uma chamada de IA:

//    - Zera campos cruzados por tipo: se opportunityType != PROJECT, minimumBudget/maximumBudget/deadline saem sempre null, não importa o que o modelo tenha respondido; o mesmo pro grupo de JOB (contractType/salário/benefits/startDate/workload). Isso existe porque validateByType() rejeitaria a mistura na publicação — aqui só evita confundir a revisão da empresa antes disso.
//    - Range de orçamento/salário: se min > max, descarta os dois e marca ambos em lowConfidenceFields.
//    - Datas (deadline, startDate): descarta se estiver no passado ou > 5 anos no futuro.
//    - CEP: só aceita se tiver exatamente 8 dígitos após remover não-numéricos; qualquer outra coisa vira null (a empresa digita manualmente).
//    - maxPositions: descarta se <= 0.
//    - Casamento de skills (matchSkills): para cada nome extraído, normaliza (minúsculas, sem acento) e busca no catálogo real (SkillRepository.findAllByActiveTrue()). Achou → foundInCatalog=true + matchedSkillId/Name; não achou → foundInCatalog=false, sem id. Nunca cria skill nova.
    private AiExtractionResponseDTO normalize(AiExtractionResponseDTO raw) {
        AiOpportunityExtractionDTO s = raw.suggestion();
        if (s == null) {
            return new AiExtractionResponseDTO(emptySuggestion(), List.of());
        }

        Set<String> lowConfidence = raw.lowConfidenceFields() != null
                ? new LinkedHashSet<>(raw.lowConfidenceFields())
                : new LinkedHashSet<>();

        boolean isProject = s.opportunityType() == OpportunityType.PROJECT;
        boolean isJob = s.opportunityType() == OpportunityType.JOB;

        // Nunca deixa os dois blocos de campos preenchidos ao mesmo tempo, independente do que
        // o modelo tenha respondido — validateByType rejeitaria isso na criação de qualquer
        // forma, então zera aqui defensivamente para não confundir a revisão da empresa.
        Double minimumBudget = isProject ? sanitizePositive(s.minimumBudget(), "minimumBudget", lowConfidence) : null;
        Double maximumBudget = isProject ? sanitizePositive(s.maximumBudget(), "maximumBudget", lowConfidence) : null;
        if (minimumBudget != null && maximumBudget != null && minimumBudget > maximumBudget) {
            lowConfidence.add("minimumBudget");
            lowConfidence.add("maximumBudget");
            minimumBudget = null;
            maximumBudget = null;
        }
        LocalDate deadline = isProject ? sanitizeFutureDate(s.deadline(), "deadline", lowConfidence) : null;

        Double monthlySalaryMin = isJob ? sanitizePositive(s.monthlySalaryMin(), "monthlySalaryMin", lowConfidence) : null;
        Double monthlySalaryMax = isJob ? sanitizePositive(s.monthlySalaryMax(), "monthlySalaryMax", lowConfidence) : null;
        if (monthlySalaryMin != null && monthlySalaryMax != null && monthlySalaryMin > monthlySalaryMax) {
            lowConfidence.add("monthlySalaryMin");
            lowConfidence.add("monthlySalaryMax");
            monthlySalaryMin = null;
            monthlySalaryMax = null;
        }
        var contractType = isJob ? s.contractType() : null;
        var benefits = isJob && s.benefits() != null ? s.benefits() : List.<String>of();
        LocalDate startDate = isJob ? sanitizeFutureDate(s.startDate(), "startDate", lowConfidence) : null;
        Integer workloadHoursPerWeek = isJob ? sanitizePositiveInt(s.workloadHoursPerWeek()) : null;

        Integer maxPositions = s.maxPositions() != null && s.maxPositions() > 0 ? s.maxPositions() : null;

        String cep = sanitizeCep(s.cep());

        return new AiExtractionResponseDTO(
                new AiOpportunityExtractionDTO(
                        blankToNull(s.title()),
                        blankToNull(s.description()),
                        s.opportunityType(),
                        s.type(),
                        s.workMode(),
                        s.experienceLevel(),
                        maxPositions,
                        minimumBudget,
                        maximumBudget,
                        deadline,
                        contractType,
                        monthlySalaryMin,
                        monthlySalaryMax,
                        benefits,
                        startDate,
                        workloadHoursPerWeek,
                        cep,
                        matchSkills(s.requiredSkills()),
                        matchSkills(s.niceToHaveSkills())
                ),
                List.copyOf(lowConfidence)
        );
    }

    private AiOpportunityExtractionDTO emptySuggestion() {
        return new AiOpportunityExtractionDTO(
                null, null, null, null, null, null, null,
                null, null, null,
                null, null, null, List.of(), null, null,
                null, List.of(), List.of());
    }

    private Double sanitizePositive(Double value, String fieldName, Set<String> lowConfidence) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            lowConfidence.add(fieldName);
            return null;
        }
        return value;
    }

    private Integer sanitizePositiveInt(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private LocalDate sanitizeFutureDate(LocalDate date, String fieldName, Set<String> lowConfidence) {
        if (date == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        if (date.isBefore(today) || date.isAfter(today.plusYears(5))) {
            lowConfidence.add(fieldName);
            return null;
        }
        return date;
    }

    private String sanitizeCep(String cep) {
        if (cep == null) {
            return null;
        }
        String digitsOnly = cep.replaceAll("[^0-9]", "");
        return digitsOnly.length() == 8 ? digitsOnly : null;
    }

    private String blankToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    // Casa cada nome extraído pela IA contra o catálogo real (SkillRepository), por nome
    // normalizado (trim, minúsculas, sem acento). Nunca cria skill nova (RN49) — o que não
    // casa vira foundInCatalog=false, exibido no frontend como "não encontrado no catálogo".
    private List<AiSkillSuggestionDTO> matchSkills(List<AiSkillSuggestionDTO> extracted) {
        if (extracted == null || extracted.isEmpty()) {
            return List.of();
        }

        Map<String, Skill> catalogByNormalizedName = new ConcurrentHashMap<>();
        for (Skill skill : skillRepository.findAllByActiveTrue()) {
            catalogByNormalizedName.put(normalize(skill.getName()), skill);
        }

        List<AiSkillSuggestionDTO> result = new ArrayList<>();
        for (AiSkillSuggestionDTO suggestion : extracted) {
            if (suggestion == null || suggestion.extractedName() == null || suggestion.extractedName().isBlank()) {
                continue;
            }
            Skill match = catalogByNormalizedName.get(normalize(suggestion.extractedName()));
            result.add(match != null
                    ? new AiSkillSuggestionDTO(suggestion.extractedName().trim(), match.getId(), match.getName(), true)
                    : new AiSkillSuggestionDTO(suggestion.extractedName().trim(), null, null, false));
        }
        return result;
    }

    private String normalize(String value) {
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.trim().toLowerCase();
    }
}
