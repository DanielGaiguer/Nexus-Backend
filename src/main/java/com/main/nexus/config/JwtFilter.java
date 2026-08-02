package com.main.nexus.config;

import com.main.nexus.dto.UserDTO;
import com.main.nexus.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter { // Apenas uma vez por requisicao

    @Autowired
    private TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (tokenService.validToken(token)) { // Somente se o token for valido e nao tiver expirado
                UserDTO user = tokenService.extractClaims(token);

                SimpleGrantedAuthority authority =
                        new SimpleGrantedAuthority("ROLE_" + user.role());

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, List.of(authority));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                 // Esse e o que efetivamente "loga" o usuario no sistema, e isso que as regras no security config consultas, no hasRole
            }
        }

        filterChain.doFilter(request, response);
    }
}