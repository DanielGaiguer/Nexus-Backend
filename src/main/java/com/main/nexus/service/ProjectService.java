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
        // Ao criar uma vaga, já gera o ranking automaticamente
        matchService.generateRankingForProject(saved);
        return saved;
    }

    public Project findById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found: " + id));
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

    public Project update(Project project) {
        return projectRepository.save(project);
    }

    public void closeProject(Long id) {
        Project project = findById(id);
        project.setStatus(ProjectStatus.CLOSED);
        projectRepository.save(project);
    }
    
    public void delete(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(404), "Project not found: " + id);
        }
        projectRepository.deleteById(id);
    }
}