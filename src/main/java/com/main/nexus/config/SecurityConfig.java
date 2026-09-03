package com.main.nexus.config;

import com.main.nexus.ratelimit.RateLimitFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private ConsentGateFilter consentGateFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register/professional").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register/company").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/linkedin/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register/company/linkedin").permitAll()

                // login/link/callback são redirects OAuth públicos (mesmo padrão do LinkedIn acima);
                // só o unlink exige um profissional autenticado, tratado abaixo.
                .requestMatchers(HttpMethod.GET, "/api/auth/github/login", "/api/auth/github/register", "/api/auth/github/link", "/api/auth/github/callback").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/auth/github/unlink").hasRole("PROFESSIONAL")

                .requestMatchers("/api/public/**").permitAll()

                // Webhook do Mercado Pago (Prompt 5) -- chamado server-to-server pelo MP,
                // sem sessão. A autenticidade é conferida no controller pela assinatura
                // x-signature quando MP_WEBHOOK_SECRET está configurado.
                .requestMatchers(HttpMethod.POST, "/api/payments/mercadopago/webhook").permitAll()

                // Webhook do eNotas (Prompt 6) -- server-to-server, sem sessão. O
                // controller apenas extrai o id da nota e re-consulta o eNotas.
                .requestMatchers(HttpMethod.POST, "/api/invoices/enotas/webhook").permitAll()

                .requestMatchers("/ws/**").permitAll()

                .requestMatchers(HttpMethod.GET, "/api/reputation/**").permitAll()

                .requestMatchers(HttpMethod.GET, "/api/skills").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/skills/categories").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/skills/suggest").hasAnyRole("PROFESSIONAL", "COMPANY")

                .requestMatchers("/api/professional/profile/export").authenticated()

                // Consentimento LGPD: leitura de status + re-aceite. Fica fora do
                // ConsentGateFilter (e por aqui que o usuario retido se libera).
                .requestMatchers("/api/legal/**").authenticated()

                // Exclusao de conta (LGPD). A confirmacao e publica: o token do
                // e-mail e a credencial (link aberto em outro dispositivo, sem
                // sessao). O pedido em si exige o proprio titular logado.
                .requestMatchers(HttpMethod.POST, "/api/users/me/deletion/confirm").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/users/me").authenticated()
                // Portabilidade (LGPD) -- so o proprio titular logado.
                .requestMatchers(HttpMethod.GET, "/api/users/me/export").authenticated()

                .requestMatchers("/api/notifications/**").authenticated()

                // Badges da sidebar -- vale para os 3 papéis; o service decide o
                // conteúdo pelo papel do usuário logado.
                .requestMatchers("/api/sidebar/**").authenticated()

                .requestMatchers("/api/chat/**").authenticated()

                // Chat de suporte -- lado do usuário (profissional/contratante); o service
                // valida que quem chama é o dono da conversa. O lado do Admin cai na regra
                // genérica /api/admin/** abaixo.
                .requestMatchers("/api/support/**").authenticated()

                .requestMatchers("/api/professional/stats").hasAnyRole("PROFESSIONAL", "ADMIN")

                .requestMatchers("/api/professional/*/admin-profile").hasRole("ADMIN")

                .requestMatchers("/api/professional/*/resume").hasAnyRole("PROFESSIONAL", "COMPANY")
                .requestMatchers("/api/professional/*/contact").hasRole("COMPANY")
                .requestMatchers("/api/company/*/contact").hasRole("PROFESSIONAL")

                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/company/**").hasRole("COMPANY")
                .requestMatchers("/api/projects/**").hasRole("COMPANY")
                .requestMatchers("/api/professional/**").hasRole("PROFESSIONAL")
                .requestMatchers(HttpMethod.POST, "/api/professional/resume").hasRole("PROFESSIONAL")

                .requestMatchers("/api/comparison/**").hasRole("COMPANY")

                .requestMatchers(HttpMethod.GET, "/api/score/preview").hasRole("COMPANY")
                .requestMatchers(HttpMethod.GET, "/api/score/preview/self").hasRole("PROFESSIONAL")
                    
                .requestMatchers(HttpMethod.POST,   "/api/professional/profile/photo").hasRole("PROFESSIONAL")
                .requestMatchers(HttpMethod.DELETE, "/api/professional/profile/photo").hasRole("PROFESSIONAL")
                .requestMatchers(HttpMethod.POST,   "/api/company/profile/photo").hasRole("COMPANY")
                .requestMatchers(HttpMethod.DELETE, "/api/company/profile/photo").hasRole("COMPANY")
                    
                    
                .requestMatchers("/api/analytics/company/dashboard").hasRole("COMPANY")
                .requestMatchers("/api/analytics/company/*/dashboard").hasRole("ADMIN")
                .requestMatchers("/api/analytics/professional/dashboard").hasRole("PROFESSIONAL")
                .requestMatchers("/api/analytics/professional/*/dashboard").hasRole("ADMIN")

                .requestMatchers("/api/matches/**").hasAnyRole("COMPANY", "PROFESSIONAL")

                .requestMatchers("/api/proposals/**").hasAnyRole("COMPANY", "PROFESSIONAL")

                // Exibição de avaliações (top 3 do card de preview + página dedicada) é
                // informação pública de perfil, igual ao resto do /api/public/** — precisa
                // vir antes da regra genérica de /api/reviews/** abaixo, que exige login.
                .requestMatchers(HttpMethod.GET, "/api/reviews/professional/*/count").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/reviews/company/*/count").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/reviews/professional/*/top3").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/reviews/professional/*/all").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/reviews/company/*/top3").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/reviews/company/*/all").permitAll()

                .requestMatchers("/api/reviews/**").hasAnyRole("COMPANY", "PROFESSIONAL")
                
                .requestMatchers("/api/map/**").hasAnyRole("PROFESSIONAL", "COMPANY", "ADMIN")

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            // Depois do JwtFilter: as politicas com chave por usuario precisam do
            // SecurityContext ja populado. Antes de qualquer regra de autorizacao
            // acima, para que um 429 nao seja mascarado por um 401/403.
            .addFilterAfter(rateLimitFilter, JwtFilter.class)
            // Depois do RateLimitFilter, mesma justificativa: precisa do
            // SecurityContext e roda antes da autorizacao. Barra requisicao
            // mutavel de quem nao aceitou a versao ativa dos Termos (LGPD).
            .addFilterAfter(consentGateFilter, RateLimitFilter.class);

        return http.build();
    }

    // O RateLimitFilter e @Component, entao o Spring Boot o registraria tambem na
    // cadeia do servlet container (posicao indefinida, cedo demais). Desliga esse
    // auto-registro: ele so participa da cadeia do Spring Security, na posicao
    // acima.
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    // Mesmo motivo do acima: ConsentGateFilter e @Component, entao o Spring Boot
    // o registraria tambem na cadeia do servlet container. Desliga esse
    // auto-registro -- ele so participa da cadeia do Spring Security.
    @Bean
    public FilterRegistrationBean<ConsentGateFilter> consentGateFilterRegistration(ConsentGateFilter filter) {
        FilterRegistrationBean<ConsentGateFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}