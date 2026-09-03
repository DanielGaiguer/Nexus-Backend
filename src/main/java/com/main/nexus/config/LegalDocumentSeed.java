package com.main.nexus.config;

import com.main.nexus.model.enums.LegalDocumentType;
import com.main.nexus.service.LegalDocumentService;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

// Semeia a v1 (ativa) de cada documento legal no bootstrap, se ainda nao existe
// nenhuma versao do tipo -- garante que /terms, /privacy e o cadastro sempre tem
// uma versao para referenciar. Mesmo padrao de runner one-shot do SchemaFixups.
//
// ATENCAO: o conteudo em resources/legal/*.md e uma MINUTA e precisa de revisao
// por advogado especializado em LGPD antes de uso real. O contato do Encarregado
// (DPO) vem de config (nexus.legal.dpo.*) e e substituido nos tokens
// {{DPO_NAME}} / {{DPO_EMAIL}} da Politica de Privacidade aqui. Trocar o DPO
// depois = Admin publica uma nova versao da Politica (fluxo correto de "mudanca
// relevante" da LGPD).
@Configuration
public class LegalDocumentSeed {

    private static final Logger log = LoggerFactory.getLogger(LegalDocumentSeed.class);

    @Value("${nexus.legal.dpo.name:}")
    private String dpoName;

    @Value("${nexus.legal.dpo.email:}")
    private String dpoEmail;

    @Bean
    CommandLineRunner seedLegalDocuments(LegalDocumentService legalDocumentService) {
        return args -> {
            seedOne(legalDocumentService, LegalDocumentType.TERMS_OF_USE,
                    "legal/terms-of-use.v1.md");
            seedOne(legalDocumentService, LegalDocumentType.PRIVACY_POLICY,
                    "legal/privacy-policy.v1.md");
        };
    }

    private void seedOne(LegalDocumentService service, LegalDocumentType type, String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                log.warn("LegalDocumentSeed: recurso {} nao encontrado -- {} nao foi semeado.",
                        resourcePath, type);
                return;
            }
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            content = applyDpoPlaceholders(content);
            service.seedInitialVersion(type, content);
            log.info("LegalDocumentSeed: {} v1 garantida (MINUTA -- pendente de revisao juridica).", type);
        } catch (Exception e) {
            log.warn("LegalDocumentSeed: falha ao semear {}: {}", type, e.getMessage());
        }
    }

    private String applyDpoPlaceholders(String content) {
        String name = dpoName == null || dpoName.isBlank()
                ? "[DPO NÃO CONFIGURADO — definir nexus.legal.dpo.name]" : dpoName.trim();
        String email = dpoEmail == null || dpoEmail.isBlank()
                ? "[DPO NÃO CONFIGURADO — definir nexus.legal.dpo.email]" : dpoEmail.trim();
        return content
                .replace("{{DPO_NAME}}", name)
                .replace("{{DPO_EMAIL}}", email);
    }
}
