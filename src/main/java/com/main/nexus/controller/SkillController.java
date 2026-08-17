package com.main.nexus.controller;

import com.main.nexus.dto.SkillResponseDTO;
import com.main.nexus.dto.SuggestSkillRequestDTO;
import com.main.nexus.model.Skill;
import com.main.nexus.service.SkillService;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Catálogo de skills, de uso geral (não exclusivo do admin) — consumido pelo componente
// de seleção/sugestão de skills reutilizado no perfil do profissional e na oportunidade
// da empresa. Antes disso cada role tinha sua própria cópia de "listar skills ativas"
// (AdminController, ProfessionalController, ProjectController) — esse aqui é o ponto
// único novo, sem substituir os já existentes pra não arriscar quebrar quem já os usa.
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    @Autowired
    private SkillService skillService;

    @GetMapping
    public List<SkillResponseDTO> listSkills() {
        return skillService.findAll().stream().map(this::toDTO).toList();
    }

    @GetMapping("/categories")
    public List<String> listCategories() {
        return skillService.getDistinctCategories();
    }

    // RN23 continua valendo pro catálogo em si: só admin cria/gerencia livremente
    // (AdminController). Isso aqui é uma "sugestão" restrita — profissional/empresa só
    // podem encaixar a skill numa categoria que já existe, nunca criar categoria nova.
    @PostMapping("/suggest")
    public ResponseEntity<SkillResponseDTO> suggestSkill(@RequestBody SuggestSkillRequestDTO request) {
        String name = request.name() != null ? request.name().trim() : "";
        if (name.isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), "Skill name cannot be blank.");
        }

        List<String> validCategories = skillService.getDistinctCategories();
        if (request.category() == null || !validCategories.contains(request.category())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Category '" + request.category() + "' does not exist.");
        }

        Optional<Skill> existing = skillService.findByNameIgnoreCase(name);
        if (existing.isPresent()) {
            return ResponseEntity.ok(toDTO(existing.get()));
        }

        Skill created = skillService.create(name, request.category());
        return ResponseEntity.status(201).body(toDTO(created));
    }

    private SkillResponseDTO toDTO(Skill skill) {
        return new SkillResponseDTO(skill.getId(), skill.getName(), skill.getCategory());
    }
}
