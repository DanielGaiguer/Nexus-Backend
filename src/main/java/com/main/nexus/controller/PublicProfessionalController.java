package com.main.nexus.controller;

import com.main.nexus.dto.PublicCompanyDTO;
import com.main.nexus.dto.PublicProfessionalDTO;
import com.main.nexus.dto.PublicProjectDTO;
import com.main.nexus.dto.ProjectResponseDTO;
import com.main.nexus.dto.SkillResponseDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Professional;
import com.main.nexus.model.PreviousProject;
import com.main.nexus.model.Project;
import com.main.nexus.model.ReputationMetrics;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.ReputationMetricsRepository;
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

        Double overallScore = reputationMetricsRepository.findByProfessionalId(id)
                .map(ReputationMetrics::getReputationScore)
                .orElse(null);

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
                overallScore
        );

        return ResponseEntity.ok(dto);
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
                c.getProfilePhotoUrl()
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
}
