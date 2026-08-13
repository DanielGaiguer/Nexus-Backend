package com.main.nexus.service;

import com.main.nexus.model.Company;
import com.main.nexus.model.Professional;
import com.main.nexus.model.enums.OpportunityType;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProfileCompletionService {

    // sempre obrigatórios 
    // CEP (latitude/longitude preenchidos = CEP foi resolvido)
    // preferredOpportunityTypes (pelo menos um regime)
    // skills (pelo menos uma)
    // experienceLevel
    // availabile
    // name, phone já são obrigatórios no cadastro

    public boolean isProfileComplete(Professional professional) {
        return getMissingFields(professional).isEmpty();
    }

    public List<String> getMissingFields(Professional professional) {
        List<String> missing = new ArrayList<>();

        if (professional.getLatitude() == null || professional.getLongitude() == null) {
            missing.add("CEP / Localização");
        }

        if (professional.getExperienceLevel() == null) {
            missing.add("Nível de experiência");
        }

        if (professional.getSkills() == null || professional.getSkills().isEmpty()) {
            missing.add("Skills");
        }

        if (professional.getGithubUrl() == null || professional.getGithubUrl().isBlank()) {
            missing.add("GitHub");
        }

        if (professional.getPreferredOpportunityTypes() == null
                || professional.getPreferredOpportunityTypes().isEmpty()) {
            missing.add("Tipos de oportunidade desejados (Freelance, PJ ou CLT)");
        } else {
            // Verifica se as pretensões dos regimes selecionados foram preenchidas
            for (OpportunityType type : professional.getPreferredOpportunityTypes()) {
                switch (type) {
                    case JOB -> {
                        if (professional.getExpectedSalaryCLT() == null) {
                            missing.add("Pretensão salarial CLT");
                        }
                        if (professional.getExpectedSalaryPJ() == null) {
                            missing.add("Pretensão salarial PJ");
                        }
                    }
                    case PROJECT -> {
                        if (professional.getFreelanceMinExpectation() == null
                                || professional.getFreelanceMaxExpectation() == null) {
                            missing.add("Pretensão por projeto (mínima e máxima)");
                        }
                    }
                }
            }
        }

        return missing;
    }

    // score só é calculado se o perfil estiver completo o suficiente
    // cep e pretensões são os únicos campos que, se ausentes, invalidam o score
    public boolean canParticipateInRanking(Professional professional) {
        if (professional.getLatitude() == null || professional.getLongitude() == null) {
            return false;
        }
        if (professional.getPreferredOpportunityTypes() == null
                || professional.getPreferredOpportunityTypes().isEmpty()) {
            return false;
        }
        for (OpportunityType type : professional.getPreferredOpportunityTypes()) {
            switch (type) {
                case JOB -> {
                    if (professional.getExpectedSalaryCLT() == null) return false;
                    if (professional.getExpectedSalaryPJ() == null) return false;
                }
                case PROJECT -> {
                    if (professional.getFreelanceMinExpectation() == null) return false;
                    if (professional.getFreelanceMaxExpectation() == null) return false;
                }
            }
        }
        return true;
    }

    // companies
    // CEP (latitude/longitude preenchidos = CEP foi resolvido)
    // CNPJ, telefone e descrição companyName e email já são obrigatórios no cadastro

    public boolean isProfileComplete(Company company) {
        return getMissingFields(company).isEmpty();
    }

    public List<String> getMissingFields(Company company) {
        List<String> missing = new ArrayList<>();

        if (company.getLatitude() == null || company.getLongitude() == null) {
            missing.add("CEP / Localização");
        }

        if (company.getTaxId() == null || company.getTaxId().isBlank()) {
            missing.add("CNPJ");
        }

        if (company.getPhone() == null || company.getPhone().isBlank()) {
            missing.add("Telefone");
        }

        if (company.getDescription() == null || company.getDescription().isBlank()) {
            missing.add("Descrição da empresa");
        }

        return missing;
    }
}