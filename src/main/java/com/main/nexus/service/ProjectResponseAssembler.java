package com.main.nexus.service;

import com.main.nexus.dto.ProjectResponseDTO;
import com.main.nexus.dto.PublicCompanyDTO;
import com.main.nexus.dto.SkillResponseDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Project;
import com.main.nexus.model.enums.UserType;
import java.util.Optional;
import org.hibernate.type.descriptor.java.CoercionHelper;
import org.springframework.stereotype.Component;

// Centraliza as regras de visibilidade de oportunidades entre empresas e de mascaramento
// de salário/orçamento, para que todo endpoint que expõe um Project aplique a mesma lógica.
// ADMIN sempre enxerga tudo; a empresa dona da oportunidade também sempre enxerga a própria.
@Component // gerencia esse classe como Bean, podendo injetala como @Autowired
public class ProjectResponseAssembler {

    public record Viewer(UserType type, Long companyId) {//Quem esta visualizando o projeto?
        public static final Viewer ADMIN = new Viewer(UserType.ADMIN, null);
        public static final Viewer ANONYMOUS = new Viewer(null, null);

        public static Viewer professional() {
            return new Viewer(UserType.PROFESSIONAL, null);
        }

        public static Viewer company(Long companyId) {
            return new Viewer(UserType.COMPANY, companyId);
        }

        private boolean isCompany() {
            return type == UserType.COMPANY;
        }

        private boolean isAdmin() {
            return type == UserType.ADMIN;
        }
    }

    // Para contextos em que o acesso à oportunidade já foi garantido por outra regra de
    // negócio (endpoint do próprio dono, do admin, ou feed do profissional).
    public ProjectResponseDTO toDTO(Project p, Viewer viewer, PublicCompanyDTO companyDTO) {
        return build(p, viewer, companyDTO);
    }

    // Para vitrines/listagens onde a oportunidade pode estar oculta para quem está pedindo.
    public Optional<ProjectResponseDTO> toVisibleDTO(Project p, Viewer viewer, PublicCompanyDTO companyDTO) {
        if (isHiddenFrom(p, viewer)) {
            return Optional.empty();
        }
        return Optional.of(build(p, viewer, companyDTO));
    }

    public boolean isHiddenFrom(Project p, Viewer viewer) {
        if (isOwner(p, viewer) || viewer.isAdmin()) {
            return false;
        }
        return viewer.isCompany() && Boolean.FALSE.equals(p.getVisibleToCompanies());
        //Se quem está olhando é uma empresa e o projeto está configurado para não aparecer para empresas, então está escondido.
    }

    private boolean isOwner(Project p, Viewer viewer) { //A empresa que está visualizando é a dona desse projeto?
        return viewer.isCompany() && viewer.companyId() != null
                && viewer.companyId().equals(p.getCompany().getId());
    }

    private ProjectResponseDTO build(Project p, Viewer viewer, PublicCompanyDTO companyDTO) {
        boolean fullAccess = isOwner(p, viewer) || viewer.isAdmin();
        UserType salaryRole = viewer.isCompany() ? UserType.COMPANY : UserType.PROFESSIONAL;
        boolean salaryVisible = fullAccess
                || (p.getSalaryVisibleTo() != null && p.getSalaryVisibleTo().contains(salaryRole));
        //O salário pode ser visto se o usuário tiver acesso completo OU se o tipo de usuário dele estiver autorizado a ver o salário.
        Company c = p.getCompany();

        return new ProjectResponseDTO(
                p.getId(),
                p.getTitle(),
                p.getDescription(),
                p.getWorkMode(),
                p.getExperienceLevel(),
                p.getStatus(),
                p.getMaxPositions(),
                p.getFilledPositions(),
                p.getCreatedAt(),
                p.getOpportunityType(),
                p.getType(),
                p.getRequiredSkills().stream()
                        .map(skill -> new SkillResponseDTO(
                                skill.getId(),
                                skill.getName(),
                                skill.getCategory()
                        ))
                        .toList(),
                c.getId(),
                c.getCompanyName(),

                p.getCep() != null ? p.getCep() : c.getCep(),
                p.getEffectiveLatitude(),
                p.getEffectiveLongitude(),
                p.getEffectiveCity(),
                p.getEffectiveUf(),

                salaryVisible ? p.getMinimumBudget() : null,
                salaryVisible ? p.getMaximumBudget() : null,
                p.getDeadline(),

                salaryVisible ? p.getMonthlySalaryMin() : null,
                salaryVisible ? p.getMonthlySalaryMax() : null,
                p.getContractType(),
                p.getBenefits(),
                p.getStartDate(),
                p.getWorkloadHoursPerWeek(),

                p.getVisibleToCompanies(),
                fullAccess ? p.getSalaryVisibleTo().contains(UserType.PROFESSIONAL) : null,
                fullAccess ? p.getSalaryVisibleTo().contains(UserType.COMPANY) : null,
                salaryVisible,

                companyDTO
        );
    }
}
