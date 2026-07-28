package com.main.nexus.controller;

import com.main.nexus.dto.CompanyPreviousProjectDTO;
import com.main.nexus.dto.PublicCompanyDTO;
import com.main.nexus.dto.PublicProfessionalDTO;
import com.main.nexus.dto.PublicProjectDTO;
import com.main.nexus.dto.ProjectResponseDTO;
import com.main.nexus.dto.ReputationExplanationDTO;
import com.main.nexus.dto.SkillResponseDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Professional;
import com.main.nexus.model.PreviousProject;
import com.main.nexus.model.Project;
import com.main.nexus.model.ReputationMetrics;
import com.main.nexus.model.enums.CompanyStatus;
import com.main.nexus.model.enums.ProjectStatus;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.ReputationMetricsRepository;
import com.main.nexus.service.MatchService;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicProfessionalController {

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private ReputationMetricsRepository reputationMetricsRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private MatchService matchService;

    @GetMapping("/professional/{id}")
    public ResponseEntity<PublicProfessionalDTO> getProfessional(@PathVariable Long id) {
        Optional<Professional> optional = professionalRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Professional p = optional.get();

        List<String> skillNames = p.getSkills() != null
                ? p.getSkills().stream().map(s -> s.getName()).toList()
                : List.of();

        List<PublicProjectDTO> previousProjects = p.getProjects() != null
                ? p.getProjects().stream()
                        .map(proj -> new PublicProjectDTO(
                                proj.getTitle(),
                                proj.getTechnologies(),
                                proj.getYearOfCompletion()
                        ))
                        .toList()
                : List.of();

        ReputationMetrics metrics = reputationMetricsRepository.findByProfessionalId(id).orElse(null);
        Double overallScore = metrics != null ? metrics.getReputationScore() : null;

        List<String> preferredTypeNames = p.getPreferredTypes() != null
                ? p.getPreferredTypes().stream().map(Enum::name).toList()
                : List.of();

        PublicProfessionalDTO dto = new PublicProfessionalDTO(
                p.getId(),
                p.getName(),
                p.getCity(),
                p.getUf(),
                p.getExperienceLevel() != null ? p.getExperienceLevel().name() : null,
                p.getReputation(),
                p.getAvailable(),
                skillNames,
                previousProjects,
                overallScore,
                metrics != null ? toReputationDTO(metrics) : null,
                p.getProfilePhotoUrl(),
                p.getFreelanceMinExpectation(),
                p.getFreelanceMaxExpectation(),
                preferredTypeNames
        );

        return ResponseEntity.ok(dto);
    }

    private ReputationExplanationDTO toReputationDTO(ReputationMetrics m) {
        return new ReputationExplanationDTO(
                m.getReputationScore(),
                m.getConfidenceScore() != null ? m.getConfidenceScore() * 100 : 0.0,
                m.getTotalReviews(),
                m.getTechnicalCompetence(),
                m.getCommunication(),
                m.getReliability(),
                m.getPunctuality(),
                m.getProfessionalism(),
                m.getSatisfactionAverage(),
                m.getRecommendationRate()
        );
    }

    @GetMapping("/opportunity/{id}")
    public ResponseEntity<ProjectResponseDTO> getOpportunity(@PathVariable Long id) {
        Optional<Project> optional = projectRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Project p = optional.get();

        // Only return OPEN projects
        if (p.getStatus() != com.main.nexus.model.enums.ProjectStatus.OPEN) {
            return ResponseEntity.notFound().build();
        }

        Company c = p.getCompany();
        PublicCompanyDTO companyDTO = new PublicCompanyDTO(
                c.getId(),
                c.getCompanyName(),
                c.getDescription(),
                c.getCity(),
                c.getUf(),
                c.getReputation(),
                c.getProfilePhotoUrl(),
                null,
                c.getTaxId(),
                c.getStatus().name(),
                List.of()
        );

        ProjectResponseDTO dto = new ProjectResponseDTO(
                p.getId(),
                p.getTitle(),
                p.getDescription(),
                p.getWorkMode(),
                p.getExperienceLevel(),
                p.getStatus(),
                p.getMaxPositions(),
                p.getFilledPositions(),
                p.getCreatedAt(),
                p.getOpportunityType(),
                p.getRequiredSkills().stream()
                        .map(skill -> new SkillResponseDTO(
                                skill.getId(),
                                skill.getName(),
                                skill.getCategory()
                        ))
                        .toList(),
                c.getId(),
                c.getCompanyName(),

                p.getCep() != null ? p.getCep() : c.getCep(),
                p.getEffectiveLatitude(),
                p.getEffectiveLongitude(),
                p.getEffectiveCity(),
                p.getEffectiveUf(),

                p.getMinimumBudget(),
                p.getMaximumBudget(),
                p.getDeadline(),

                p.getMonthlySalaryMin(),
                p.getMonthlySalaryMax(),
                p.getContractType(),
                p.getBenefits(),
                p.getStartDate(),
                p.getWorkloadHoursPerWeek(),

                companyDTO
        );

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/company/{id}")
    public ResponseEntity<PublicCompanyDTO> getCompany(@PathVariable Long id) {
        Optional<Company> optional = companyRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Company c = optional.get();
        if (c.getStatus() != CompanyStatus.APPROVED) {
            return ResponseEntity.notFound().build();
        }

        ReputationMetrics metrics = reputationMetricsRepository.findByCompanyId(id).orElse(null);

        List<CompanyPreviousProjectDTO> previousProjects = matchService.getPreviousProjectsByCompany(id)
                .stream()
                .map(m -> new CompanyPreviousProjectDTO(m.getId(), m.getProject().getTitle(), m.getCreatedAt()))
                .toList();

        PublicCompanyDTO dto = new PublicCompanyDTO(
                c.getId(),
                c.getCompanyName(),
                c.getDescription(),
                c.getCity(),
                c.getUf(),
                c.getReputation(),
                c.getProfilePhotoUrl(),
                metrics != null ? toReputationDTO(metrics) : null,
                c.getTaxId(),
                c.getStatus().name(),
                previousProjects
        );

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/company/{id}/projects")
    public ResponseEntity<List<ProjectResponseDTO>> getCompanyOpenProjects(@PathVariable Long id) {
        Optional<Company> optional = companyRepository.findById(id);
        if (optional.isEmpty() || optional.get().getStatus() != CompanyStatus.APPROVED) {
            return ResponseEntity.notFound().build();
        }

        Company c = optional.get();

        List<ProjectResponseDTO> openProjects = projectRepository.findByCompanyId(id).stream()
                .filter(project -> project.getStatus() == ProjectStatus.OPEN)
                .map(project -> new ProjectResponseDTO(
                        project.getId(),
                        project.getTitle(),
                        project.getDescription(),
                        project.getWorkMode(),
                        project.getExperienceLevel(),
                        project.getStatus(),
                        project.getMaxPositions(),
                        project.getFilledPositions(),
                        project.getCreatedAt(),
                        project.getOpportunityType(),
                        project.getRequiredSkills().stream()
                                .map(skill -> new SkillResponseDTO(
                                        skill.getId(),
                                        skill.getName(),
                                        skill.getCategory()
                                ))
                                .toList(),
                        c.getId(),
                        c.getCompanyName(),

                        project.getCep() != null ? project.getCep() : c.getCep(),
                        project.getEffectiveLatitude(),
                        project.getEffectiveLongitude(),
                        project.getEffectiveCity(),
                        project.getEffectiveUf(),

                        project.getMinimumBudget(),
                        project.getMaximumBudget(),
                        project.getDeadline(),

                        project.getMonthlySalaryMin(),
                        project.getMonthlySalaryMax(),
                        project.getContractType(),
                        project.getBenefits(),
                        project.getStartDate(),
                        project.getWorkloadHoursPerWeek(),

                        null
                ))
                .toList();

        return ResponseEntity.ok(openProjects);
    }
}
