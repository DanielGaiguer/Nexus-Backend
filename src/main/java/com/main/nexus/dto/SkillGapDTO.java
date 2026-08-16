package com.main.nexus.dto;

// Skill que aparece como requisito nos projetos/vagas em que o profissional teve algum
// match real (WAITING puro fora) mas que não está no perfil dele — usado no card
// "HardSkills" do dashboard, como indicação de aprendizado.
public record SkillGapDTO(
        String skillName,
        String category,
        long frequency
) {}
