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

                // login/link/callback são redirects OAuth públicos (mesmo padrão do LinkedIn acima);
                // só o unlink exige um profissional autenticado, tratado abaixo.
                .requestMatchers(HttpMethod.GET, "/api/auth/github/login", "/api/auth/github/register", "/api/auth/github/link", "/api/auth/github/callback").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/auth/github/unlink").hasRole("PROFESSIONAL")

                .requestMatchers("/api/public/**").permitAll()

                .requestMatchers("/ws/**").permitAll()

                .requestMatchers(HttpMethod.GET, "/api/reputation/**").permitAll()

                .requestMatchers(HttpMethod.GET, "/api/skills").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/skills/categories").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/skills/suggest").hasAnyRole("PROFESSIONAL", "COMPANY")

                .requestMatchers("/api/professional/profile/export").authenticated()
                    
                .requestMatchers("/api/notifications/**").authenticated()

                .requestMatchers("/api/chat/**").authenticated()

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
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}