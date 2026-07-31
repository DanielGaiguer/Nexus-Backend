package com.main.nexus.scheduler;

import com.main.nexus.model.Project;
import com.main.nexus.model.enums.UserType;
import com.main.nexus.repository.ProjectRepository;
import jakarta.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// Roda uma única vez a cada subida da aplicação: garante que projetos criados antes da
// feature de visibilidade (empresa/salário) fiquem com o comportamento antigo — totalmente
// visíveis — em vez de NULL/vazio, que passaria a significar "oculto" a partir de agora.
//
// Usa @PostConstruct (não ApplicationRunner) de propósito: ApplicationRunner só executa
// depois que o Tomcat embutido já está aceitando conexões, o que abriria uma janela real
// em que uma requisição poderia ver projetos antigos ainda não migrados (salaryVisibleTo
// vazio == mascarado). @PostConstruct roda durante a inicialização dos beans, antes do
// servidor subir.
@Component
public class ProjectVisibilityBackfill {

    @Autowired
    private ProjectRepository projectRepository;

    @PostConstruct
    public void backfill() {
        List<Project> all = projectRepository.findAll();
        for (Project project : all) {
            boolean dirty = false;

            if (project.getVisibleToCompanies() == null) {
                project.setVisibleToCompanies(true);
                dirty = true;
            }

            // Só mexe no salaryVisibleTo se ele nunca foi configurado pela aplicação.
            // Um conjunto vazio já configurado é uma escolha explícita da empresa (esconder
            // de todo mundo) e não pode ser confundida com "projeto antigo, nunca migrado" —
            // senão a escolha seria desfeita a cada reinício.
            if (project.getSalaryVisibilityConfigured() == null) {
                Set<UserType> both = new HashSet<>(Set.of(UserType.PROFESSIONAL, UserType.COMPANY));
                project.setSalaryVisibleTo(both);
                project.setSalaryVisibilityConfigured(true);
                dirty = true;
            }

            if (dirty) {
                projectRepository.save(project);
            }
        }
    }
}
