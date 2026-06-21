package com.main.nexus.service;

import com.main.nexus.model.Company;
import com.main.nexus.model.Project;
import com.main.nexus.model.enums.ProjectStatus;
import com.main.nexus.repository.ProjectRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private MatchService matchService;

    public Project save(Project project) {
        Project saved = projectRepository.save(project);
        matchService.generateRankingForProject(saved);
        return saved;
    }
    
    public Project update(Project project) {
        Project saved = projectRepository.save(project);
        matchService.recalculateRankingForProject(saved);
        return saved;
    }
    
    public Project findById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found: " + id));
    }

    // ── Novo: busca validando que o projeto pertence à empresa ──────────────
    public Project findByIdAndCompany(Long id, Long companyId) {
        Project project = findById(id);
        validateOwnership(project, companyId);
        return project;
    }

    public List<Project> findByCompany(Company company) {
        return projectRepository.findByCompanyId(company.getId());
    }

    public List<Project> findAllOpen() {
        return projectRepository.findByStatus(ProjectStatus.OPEN);
    }

    public List<Project> findAll() {
        return projectRepository.findAll();
    }

    public void closeProject(Long id, Long companyId) {
        Project project = findByIdAndCompany(id, companyId);
        project.setStatus(ProjectStatus.CLOSED);
        projectRepository.save(project);
    }

    public void delete(Long id, Long companyId) {
        Project project = findByIdAndCompany(id, companyId);
        projectRepository.delete(project);
    }

    // ── Validação de posse ───────────────────────────────────────────────────
    private void validateOwnership(Project project, Long companyId) {
        if (!project.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(403),
                    "This project does not belong to your company.");
        }
    }
}