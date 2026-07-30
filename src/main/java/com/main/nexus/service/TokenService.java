package com.main.nexus.service;

import com.main.nexus.dto.UserDTO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class TokenService {

    @Value("${api.security.token.secret}")
    private String secret;

    public SecretKey getKeySign() {
        byte[] keyBytes = Decoders.BASE64.decode(this.secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(UserDTO user) {
        if (user.id() == null || user.email().isBlank() || user.role().isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), "Missing user data.");
        }

        return Jwts.builder()
                .subject(user.email())
                .claim("id", user.id())
                .claim("email", user.email())
                .claim("role", user.role())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(this.getKeySign())
                .compact();
    }

    public UserDTO extractClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getKeySign())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new UserDTO(
                claims.get("id", Long.class),
                claims.get("email", String.class),
                claims.get("role", String.class)
        );
    }

    public boolean validToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getKeySign())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // Ticket temporário de cadastro via LinkedIn 
    // Carrega a identidade já verificada pelo LinkedIn (sub/email/name) entre
    // o callback do OAuth e o formulário de finalização do cadastro de
    // empresa (o LinkedIn não fornece razão social, então a empresa precisa
    // de um passo extra para informá-la). Curta duração, propósito fixo.

    private static final String LINKEDIN_TICKET_PURPOSE = "linkedin_register";

    public record LinkedInTicket(String sub, String email, String name, String picture) {}

    public String generateLinkedInTicket(String sub, String email, String name, String picture) {
        return Jwts.builder()
                .subject(sub)
                .claim("sub", sub)
                .claim("email", email)
                .claim("name", name)
                .claim("picture", picture)
                .claim("purpose", LINKEDIN_TICKET_PURPOSE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(this.getKeySign())
                .compact();
    }

    public LinkedInTicket extractLinkedInTicket(String ticket) {
        Claims claims = Jwts.parser()
                .verifyWith(getKeySign())
                .build()
                .parseSignedClaims(ticket)
                .getPayload();

        if (!LINKEDIN_TICKET_PURPOSE.equals(claims.get("purpose", String.class))) {
            throw new JwtException("Invalid ticket purpose.");
        }

        return new LinkedInTicket(
                claims.get("sub", String.class),
                claims.get("email", String.class),
                claims.get("name", String.class),
                claims.get("picture", String.class)
        );
    }
}