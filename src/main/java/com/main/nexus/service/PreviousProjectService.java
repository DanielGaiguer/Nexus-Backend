package com.main.nexus.service;

import com.main.nexus.dto.PreviousProjectDTO;
import com.main.nexus.model.PreviousProject;
import com.main.nexus.repository.PreviousProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PreviousProjectService {

    private static final int MAX_TECHNOLOGIES = 40;
    private static final int MAX_TECHNOLOGY_LENGTH = 100;

    @Autowired
    private PreviousProjectRepository previousProjectRepository;

    public List<PreviousProjectDTO> findByProfessional(Long professionalId) {
        return previousProjectRepository.findByProfessionalId(professionalId)
                .stream()
                .map(p -> new PreviousProjectDTO(
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
            return new ArrayList<>();
        }

        // Coleta em uma ArrayList mutável (em vez de .toList(), que é imutável) -
        // o Hibernate precisa mutar essa lista ao sincronizar o PersistentBag
        // gerenciado da entidade durante o merge/flush, senão lança
        // UnsupportedOperationException.
        List<String> normalized = technologies.stream()
                .filter(tech -> tech != null && !tech.isBlank()) // Remove nulos e vazios
                .map(String::trim) // Remove espacos do inicio e final de cada texto
                .collect(Collectors.toCollection(ArrayList::new));

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

    public PreviousProject update(Long id, Long professionalId, PreviousProjectDTO request) {
        PreviousProject project = previousProjectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found: " + id));

        // profissional só edita o próprio projeto
        if (!project.getProfessional().getId().equals(professionalId)) {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(403), "Not authorized.");
        }

        project.setTitle(request.title());
        project.setDescription(request.description());
        project.setTechnologies(normalizeTechnologies(request.technologies()));
        project.setYearOfCompletion(request.yearOfCompletion());

        return previousProjectRepository.save(project);
    }

    public void delete(Long id, Long professionalId) {
        PreviousProject project = previousProjectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Project not found: " + id));

        // profissional só deleta o próprio projeto
        if (!project.getProfessional().getId().equals(professionalId)) {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(403), "Not authorized.");
        }

        previousProjectRepository.deleteById(id);
    }
}