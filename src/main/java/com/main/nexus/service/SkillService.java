package com.main.nexus.service;

import com.main.nexus.model.Skill;
import com.main.nexus.repository.SkillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class SkillService {

    @Autowired
    private SkillRepository skillRepository;

    public List<Skill> findAll() {
        return skillRepository.findAll();
    }

    public Skill findById(Long id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Skill not found: " + id));
    }

    public List<Skill> findAllById(List<Long> ids) {
        return skillRepository.findAllById(ids);
    }

    public Skill create(String name, String category) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(400), "Skill name is required.");
        }

        if (skillRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(409), "Skill already exists: " + name);
        }

        Skill skill = new Skill();
        skill.setName(name.trim());
        skill.setCategory(category != null ? category.trim() : null);

        return skillRepository.save(skill);
    }

    public void delete(Long id) {
        if (!skillRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(404), "Skill not found: " + id);
        }
        skillRepository.deleteById(id);
    }
}