package com.main.nexus.config;

import com.main.nexus.model.Company;
import com.main.nexus.model.CompanyMember;
import com.main.nexus.model.enums.CompanyMemberRole;
import com.main.nexus.model.enums.CompanyMemberStatus;
import com.main.nexus.repository.CompanyMemberRepository;
import com.main.nexus.repository.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Toda Company já cadastrada vira "1 membro OWNER ACTIVE" (o próprio User atual),
// sem exigir ação manual -- mesmo espírito one-shot idempotente do SchemaFixups e
// do LegalDocumentSeed. Roda depois do ddl-auto criar tb_company_member.
//
// Idempotente: pula qualquer Company que já tem linha em tb_company_member.
// NÃO toca em tb_company.user_id (o @OneToOne legado continua existindo).
@Configuration
public class CompanyMemberBackfill {

    private static final Logger log = LoggerFactory.getLogger(CompanyMemberBackfill.class);

    @Bean
    CommandLineRunner backfillCompanyOwners(CompanyRepository companyRepository,
                                            CompanyMemberRepository companyMemberRepository) {
        return args -> {
            try {
                int created = 0;
                for (Company company : companyRepository.findAll()) {
                    if (company.getUser() == null) {
                        // Cenário degenerado (FK NOT NULL deveria impedir) -- não trava o boot.
                        continue;
                    }
                    if (!companyMemberRepository.findByCompanyId(company.getId()).isEmpty()) {
                        continue;
                    }

                    CompanyMember owner = new CompanyMember();
                    owner.setCompany(company);
                    owner.setUser(company.getUser());
                    owner.setRole(CompanyMemberRole.OWNER);
                    owner.setStatus(CompanyMemberStatus.ACTIVE);
                    companyMemberRepository.save(owner);
                    created++;
                }

                if (created > 0) {
                    log.info("CompanyMemberBackfill: {} empresa(s) receberam a linha OWNER inicial "
                           + "em tb_company_member.", created);
                }
            } catch (Exception e) {
                // Banco novo, permissão ausente ou corrida com outro boot: não é fatal
                // -- o registro de empresa também cria a linha, e um próximo boot repete.
                log.warn("CompanyMemberBackfill: não foi possível semear os membros OWNER: {}",
                        e.getMessage());
            }
        };
    }
}
