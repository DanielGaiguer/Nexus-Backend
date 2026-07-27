package com.main.nexus.service;

import com.main.nexus.dto.ScoreBreakdownDTO;
import com.main.nexus.dto.ScorePreviewResponseDTO;
import com.main.nexus.model.Match;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScorePreviewService {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchService matchService;

    // Empresa consultando o score de um profissional em um dos seus projetos
    public ScorePreviewResponseDTO previewForCompany(Long companyId, Long professionalId, Long projectId) {
        Project project = findProject(projectId);

        if (!project.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This project does not belong to your company.");
        }

        Professional professional = findProfessional(professionalId);
        return buildPreview(professional, project);
    }

    // Profissional consultando seu próprio score em uma vaga/projeto
    public ScorePreviewResponseDTO previewForProfessional(Long professionalId, Long projectId) {
        Project project = findProject(projectId);
        Professional professional = findProfessional(professionalId);
        return buildPreview(professional, project);
    }

    // Reaproveita o Match existente (se houver) para não divergir do score já
    // visto em outra tela; senão monta um Match transitório (não salvo) só
    // para calcular o breakdown sob demanda.
    private ScorePreviewResponseDTO buildPreview(Professional professional, Project project) {
        Match match = matchRepository
                .findByProjectIdAndProfessionalId(project.getId(), professional.getId())
                .orElseGet(() -> {
                    Match transientMatch = new Match();
                    transientMatch.setProject(project);
                    transientMatch.setProfessional(professional);
                    transientMatch.setMatchScore(matchService.getScore(professional, project));
                    return transientMatch;
                });

        ScoreBreakdownDTO breakdown = matchService.getScoreBreakdown(professional, project, match);

        return new ScorePreviewResponseDTO(
                match.getMatchScore(),
                match.getStatus().name(),
                match.getId(),
                breakdown
        );
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found."));
    }

    private Professional findProfessional(Long professionalId) {
        return professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional not found."));
    }
}
