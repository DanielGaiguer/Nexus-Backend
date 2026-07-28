package com.main.nexus.config;

import org.springframework.beans.factory.annotation.Autowired;
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

                .requestMatchers("/api/public/**").permitAll()

                // Handshake SockJS/STOMP (/ws/info, /ws/*/*/{xhr,websocket}, ...) não
                // envia o header Authorization — a autenticação real acontece no
                // WebSocketAuthInterceptor durante o CONNECT (ver WebSocketConfig).
                .requestMatchers("/ws/**").permitAll()

                // Reputação agregada não contém dados sensíveis — pode ser exibida
                // nas páginas públicas de perfil sem exigir autenticação
                .requestMatchers(HttpMethod.GET, "/api/reputation/**").permitAll()

                // Export de PDF: qualquer autenticado pode baixar
                .requestMatchers("/api/professional/profile/export").authenticated()
                    
                .requestMatchers("/api/notifications/**").authenticated()

                .requestMatchers("/api/chat/**").authenticated()

                // Stats: profissional e admin acessam
                .requestMatchers("/api/professional/stats").hasAnyRole("PROFESSIONAL", "ADMIN")

                // Admin pode ver perfil de profissional por ID
                .requestMatchers("/api/professional/*/admin-profile").hasRole("ADMIN")

                // Regras específicas de /api/professional e /api/company precisam vir
                // ANTES das regras amplas hasRole abaixo — do contrário a regra ampla
                // (que também casa com esses paths) sempre vence primeiro e a específica
                // nunca é avaliada (era o caso do download de currículo por empresas).
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

                // Match: tanto empresa quanto profissional acessam
                .requestMatchers("/api/matches/**").hasAnyRole("COMPANY", "PROFESSIONAL")

                // Reviews: ambos podem avaliar
                .requestMatchers("/api/reviews/**").hasAnyRole("COMPANY", "PROFESSIONAL")
                
                 //Libera o mapa para profissional, empresa e admin
                 .requestMatchers("/api/map/**").hasAnyRole("PROFESSIONAL", "COMPANY", "ADMIN")

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}