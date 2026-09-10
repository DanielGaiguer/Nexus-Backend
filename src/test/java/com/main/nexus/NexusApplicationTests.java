package com.main.nexus;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// @ActiveProfiles("test") existe por um motivo concreto: este teste sobe o contexto
// COMPLETO contra o MySQL de desenvolvimento, e o Spring executa os CommandLineRunner junto.
// Antes disso, cada `mvnw test` semeava/alterava linhas no banco de dev (itens do inventário
// comportamental, documentos legais, usuário admin, backfill de membros, ALTER TABLE dos
// fixups). Os beans de seed estão marcados com @Profile("!test") e agora ficam de fora.
//
// O profile NÃO troca o banco: `ddl-auto=update` continua rodando contra o mesmo MySQL, de
// propósito -- o projeto usa columnDefinition com sintaxe específica de MySQL em várias
// entidades, e um banco em memória testaria um schema diferente do real.
@SpringBootTest
@ActiveProfiles("test")
class NexusApplicationTests {

	@Test
	void contextLoads() {
	}

}
