package com.main.nexus.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

// Correções pontuais de schema que o ddl-auto=update não faz sozinho (ele cria
// coluna/tabela/constraint nova, mas nunca REMOVE nada).
//
// Prompt 2 da camada financeira: MatchStatusCheck deixou de ser 1 por match
// (@OneToOne, com índice único em match_id) e passou a ser 1 por match POR LADO
// (unique composto match_id + answered_by). O índice único antigo, só em
// match_id, continuaria no banco e bloquearia a 2ª resposta (a do outro lado).
// Este runner detecta e derruba esse índice legado uma única vez.
@Configuration
public class SchemaFixups {

    private static final Logger log = LoggerFactory.getLogger(SchemaFixups.class);

    @Bean
    CommandLineRunner dropLegacyMatchStatusCheckUniqueIndex(JdbcTemplate jdbc) {
        return args -> {
            try {
                // Índices ÚNICOS (NON_UNIQUE = 0) de tb_match_status_check que NÃO
                // incluem a coluna answered_by -> ou seja, não são o composto novo
                // nem a PK. O que sobra é o único legado só em match_id.
                List<String> legacy = jdbc.queryForList(
                        "SELECT DISTINCT s.INDEX_NAME "
                      + "FROM information_schema.STATISTICS s "
                      + "WHERE s.TABLE_SCHEMA = DATABASE() "
                      + "  AND s.TABLE_NAME = 'tb_match_status_check' "
                      + "  AND s.NON_UNIQUE = 0 "
                      + "  AND s.INDEX_NAME <> 'PRIMARY' "
                      + "  AND s.INDEX_NAME NOT IN ( "
                      + "     SELECT INDEX_NAME FROM information_schema.STATISTICS "
                      + "     WHERE TABLE_SCHEMA = DATABASE() "
                      + "       AND TABLE_NAME = 'tb_match_status_check' "
                      + "       AND COLUMN_NAME = 'answered_by') "
                      + "  AND s.INDEX_NAME IN ( "
                      + "     SELECT INDEX_NAME FROM information_schema.STATISTICS "
                      + "     WHERE TABLE_SCHEMA = DATABASE() "
                      + "       AND TABLE_NAME = 'tb_match_status_check' "
                      + "       AND COLUMN_NAME = 'match_id')",
                        String.class);

                for (String index : legacy) {
                    jdbc.execute("ALTER TABLE tb_match_status_check DROP INDEX `" + index + "`");
                    log.info("SchemaFixups: derrubado índice único legado '{}' em tb_match_status_check "
                           + "(match_id passou a aceitar 1 linha por lado).", index);
                }
            } catch (Exception e) {
                // Banco novo (tabela criada já no formato certo) ou permissão ausente:
                // não é fatal -- só registra.
                log.warn("SchemaFixups: não foi possível checar/derrubar o índice legado "
                       + "de tb_match_status_check: {}", e.getMessage());
            }
        };
    }

    // Chamado de suporte aberto pelo usuário: a conversa passa a poder existir sem
    // um admin dono (opened_by_admin nulo até o 1º admin responder). O ddl-auto
    // cria coluna nova, mas nunca RELAXA um NOT NULL já existente -- então a coluna
    // legada continuaria NOT NULL e barraria o INSERT do chamado. MODIFY ... NULL
    // é idempotente (no-op se a coluna já aceita NULL).
    @Bean
    CommandLineRunner relaxSupportConversationOpenedByAdmin(JdbcTemplate jdbc) {
        return args -> {
            try {
                Long notNull = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.COLUMNS "
                      + "WHERE TABLE_SCHEMA = DATABASE() "
                      + "  AND TABLE_NAME = 'tb_support_conversation' "
                      + "  AND COLUMN_NAME = 'opened_by_admin' "
                      + "  AND IS_NULLABLE = 'NO'",
                        Long.class);
                if (notNull != null && notNull > 0) {
                    jdbc.execute("ALTER TABLE tb_support_conversation "
                               + "MODIFY COLUMN opened_by_admin BIGINT NULL");
                    log.info("SchemaFixups: tb_support_conversation.opened_by_admin agora aceita NULL "
                           + "(chamado de suporte pode ser aberto pelo próprio usuário).");
                }
            } catch (Exception e) {
                // Banco novo (coluna já criada nullable) ou permissão ausente: não é fatal.
                log.warn("SchemaFixups: não foi possível relaxar "
                       + "tb_support_conversation.opened_by_admin: {}", e.getMessage());
            }
        };
    }

    // NFS-e da plataforma personalizada: o NfseInvoice passou a apontar para UMA
    // de duas cobranças (comissão OU mensalidade de plataforma), então
    // commission_charge_id deixou de ser obrigatório. O ddl-auto não relaxa
    // NOT NULL existente. MODIFY ... NULL é idempotente.
    @Bean
    CommandLineRunner relaxNfseInvoiceCommissionCharge(JdbcTemplate jdbc) {
        return args -> {
            try {
                Long notNull = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.COLUMNS "
                      + "WHERE TABLE_SCHEMA = DATABASE() "
                      + "  AND TABLE_NAME = 'tb_nfse_invoice' "
                      + "  AND COLUMN_NAME = 'commission_charge_id' "
                      + "  AND IS_NULLABLE = 'NO'",
                        Long.class);
                if (notNull != null && notNull > 0) {
                    jdbc.execute("ALTER TABLE tb_nfse_invoice "
                               + "MODIFY COLUMN commission_charge_id BIGINT NULL");
                    log.info("SchemaFixups: tb_nfse_invoice.commission_charge_id agora aceita NULL "
                           + "(NFS-e pode vir de mensalidade de plataforma).");
                }
            } catch (Exception e) {
                log.warn("SchemaFixups: não foi possível relaxar "
                       + "tb_nfse_invoice.commission_charge_id: {}", e.getMessage());
            }
        };
    }
}
