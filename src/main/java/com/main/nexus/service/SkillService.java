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

    // Só skills ativas entram no catálogo (seleção em perfil/projeto). Uma skill
    // deletada continua no banco, mas some daqui.
    public List<Skill> findAll() {
        return skillRepository.findAllByActiveTrue();
    }

    // Resolve por id independente de active — precisa continuar funcionando para
    // associações já existentes (Professional.skills / Project.requiredSkills)
    // mesmo depois que a skill for removida do catálogo.
    public Skill findById(Long id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Skill not found: " + id));
    }

    public List<Skill> findAllById(List<Long> ids) {
        return skillRepository.findAllById(ids);
    }

    public java.util.Optional<Skill> findByNameIgnoreCase(String name) {
        return skillRepository.findByNameIgnoreCase(name);
    }

    // Categorias distintas em uso pelo catálogo ativo — usado pra validar a categoria
    // informada em /api/skills/suggest (só aceita categoria já existente, não cria nova).
    public List<String> getDistinctCategories() {
        return findAll().stream()
                .map(Skill::getCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted()
                .toList();
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

    // Soft delete: nunca remove a linha fisicamente, só desativa. Uma remoção real
    // quebraria as tabelas de junção tb_professional_skill/tb_job_skill para quem
    // já tem essa skill associada (cascade silenciosa ou violação de FK, dependendo
    // de como o ddl-auto=update gerou as constraints).
    public void delete(Long id) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Skill not found: " + id));

        if (!Boolean.TRUE.equals(skill.getActive())) {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(409), "Skill already deleted: " + id);
        }

        skill.setActive(false);
        skillRepository.save(skill);
    }
}