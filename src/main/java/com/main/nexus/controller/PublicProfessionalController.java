package com.main.nexus.controller;

import com.main.nexus.dto.PublicProfessionalDTO;
import com.main.nexus.dto.PublicProjectDTO;
import com.main.nexus.model.Professional;
import com.main.nexus.model.PreviousProject;
import com.main.nexus.model.ReputationMetrics;
import com.main.nexus.repository.ProfessionalRepository;
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
}
