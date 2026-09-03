package com.main.nexus.config;

import com.main.nexus.model.enums.AuditTargetType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um método (de controller administrativo) que expõe dado pessoal de um
 * usuário específico. O {@link DataAccessAuditAspect} intercepta cada chamada
 * bem-sucedida e grava uma linha em {@code tb_data_access_log}: qual admin,
 * qual usuário-alvo, qual ação, quando.
 *
 * Opção de projeto (Prompt 4): a anotação fica no CONTROLLER, não no service.
 * Motivo -- os pontos de acesso a dado pessoal pelo admin estão concentrados em
 * ~20 endpoints, cada um com o id do alvo como {@code @PathVariable}/param; o
 * AdminController faz a montagem inline (não há "um método de service que busca
 * o dado do usuário para o admin" -- é {@code findById} + assembler espalhados),
 * e anotar {@code professionalService.findById} dispararia para todo o app, não
 * só para o admin. Anotar o controller, com um alvo tipado, é mais preciso e
 * mais fácil de manter do que casar por padrão de URL num filtro.
 *
 * O "motivo" do acesso é a própria {@link #action()} -- o admin não digita
 * justificativa a cada clique (Rule 3).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditDataAccess {

    /** Rótulo legível da ação (vira o "motivo"). Ex.: "Visualizou perfil do profissional". */
    String action();

    /** Tipo do id recebido pelo método, para resolver o usuário-alvo. */
    AuditTargetType target() default AuditTargetType.NONE;

    /**
     * Nome do parâmetro do método que carrega o id do alvo. Se vazio e
     * {@link #target()} != NONE, usa o primeiro parâmetro {@code Long}.
     * O parâmetro pode ser nulo em runtime (ex.: filtro opcional
     * {@code companyId}) -- nesse caso registra com alvo nulo.
     */
    String param() default "";
}
