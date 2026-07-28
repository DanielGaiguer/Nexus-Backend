package com.main.nexus.service;

import com.main.nexus.dto.PreviousProjectRequestDTO;
import com.main.nexus.model.PreviousProject;
import com.main.nexus.model.Professional;
import com.main.nexus.repository.PreviousProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PreviousProjectService {

    private static final int MAX_TECHNOLOGIES = 15;
    private static final int MAX_TECHNOLOGY_LENGTH = 100;

    @Autowired
    private PreviousProjectRepository previousProjectRepository;

    public List<PreviousProjectRequestDTO> findByProfessional(Long professionalId) {
        return previousProjectRepository.findByProfessionalId(professionalId)
                .stream()
                .map(p -> new PreviousProjectRequestDTO(
                        p.getId(),
                        p.getTitle(),
                        p.getDescription(),
                        p.getTechnologies(),
                        p.getYearOfCompletion()
                ))
                .toList();
    }

    public PreviousProject save(PreviousProject project) {
        project.setTechnologies(normalizeTechnologies(project.getTechnologies()));
        return previousProjectRepository.save(project);
    }

    private List<String> normalizeTechnologies(List<String> technologies) {
        if (technologies == null) {
            return List.of();
        }

        List<String> normalized = technologies.stream()
                .filter(tech -> tech != null && !tech.isBlank())
                .map(String::trim)
                .toList();

        if (normalized.size() > MAX_TECHNOLOGIES) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Máximo de " + MAX_TECHNOLOGIES + " tecnologias por projeto.");
        }

        for (String tech : normalized) {
            if (tech.length() > MAX_TECHNOLOGY_LENGTH) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Tecnologia '" + tech + "' excede o limite de " + MAX_TECHNOLOGY_LENGTH + " caracteres.");
            }
        }

        return normalized;
    }

    public void delete(Long id, Long professionalId) {
        PreviousProject project = previousProjectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found: " + id));

        // Garante que o profissional só deleta o próprio projeto
        if (!project.getProfessional().getId().equals(professionalId)) {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(403), "Not authorized.");
        }

        previousProjectRepository.deleteById(id);
    }
}