package com.main.nexus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.nexus.dto.GitHubUserDTO;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Integração com o "Sign In with GitHub" (OAuth Apps). Mesmo desenho do LinkedInService:
 * troca o code por um access_token server-to-server (client_secret nunca sai do backend)
 * e usa esse token para buscar a identidade do usuário — aqui, GET /user da API do GitHub,
 * que já fornece o login e a URL pública do perfil (html_url), diferente do LinkedIn.
 */
@Service
public class GitHubService {

    private static final String AUTHORIZATION_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final String EMAILS_URL = "https://api.github.com/user/emails";
    private static final String SCOPE = "read:user user:email";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${github.client-id}")
    private String clientId;

    @Value("${github.client-secret}")
    private String clientSecret;

    @Value("${github.redirect-uri}")
    private String redirectUri;

    public String buildAuthorizationUrl(String state) { // monta a URL para onde o browser é redirecionado para o usuário autorizar o app no GitHub
        return AUTHORIZATION_URL
                + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(SCOPE, StandardCharsets.UTF_8) // identidade básica + e-mail, não acesso a repositórios
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8); // carrega o modo (login/link) através do ciclo OAuth, igual ao LinkedIn
    }

    public GitHubUserDTO exchangeCodeForUser(String code) { // orquestra as duas chamadas server-to-server
        String accessToken = fetchAccessToken(code);
        return fetchUser(accessToken);
    }

    // troca o code (recebido no callback) por um access_token, via POST form-urlencoded ao
    // endpoint /login/oauth/access_token, autenticando a aplicação com client_id + client_secret.
    // Accept: application/json é necessário — sem ele o GitHub responde em application/x-www-form-urlencoded.
    private String fetchAccessToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        com.fasterxml.jackson.databind.JsonNode tokenNode;
        try {
            String response = restTemplate.postForObject(TOKEN_URL, request, String.class);
            tokenNode = objectMapper.readTree(response);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "Failed to exchange authorization code with GitHub.", e);
        }

        String accessToken = tokenNode.path("access_token").asText(null);
        if (accessToken == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "GitHub did not return an access token.");
        }
        return accessToken;
    }

    // usa o access_token como Bearer para chamar GET /user, extraindo id/login/name/email/
    // avatar_url/html_url — id é a chave primária de identidade (estável, mesmo que o login mude).
    private GitHubUserDTO fetchUser(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        GitHubUserDTO user;
        try {
            var response = restTemplate.exchange(USER_URL, HttpMethod.GET, request, String.class);
            user = objectMapper.readValue(response.getBody(), GitHubUserDTO.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "Failed to fetch GitHub user info.", e);
        }

        if (user.id() == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "GitHub user info response is missing 'id'.");
        }

        // GET /user só traz o "email" se a pessoa escolheu um e-mail público em
        // github.com/settings/profile — é um campo raro de estar preenchido, diferente de
        // "manter e-mails privados" em github.com/settings/emails. Por isso, sempre que vier
        // nulo aqui, buscamos em /user/emails (liberado pelo escopo user:email) o e-mail
        // verificado que a pessoa realmente usa — sem depender daquela preferência de perfil.
        if (user.email() == null || user.email().isBlank()) {
            String verifiedEmail = fetchPrimaryVerifiedEmail(accessToken);
            if (verifiedEmail != null) {
                user = new GitHubUserDTO(user.id(), user.login(), user.name(), verifiedEmail,
                        user.avatarUrl(), user.htmlUrl());
            }
        }

        return user;
    }

    // GET /user/emails devolve todos os e-mails da conta com suas flags (primary/verified),
    // independente do e-mail "público" do perfil. Falha em silêncio (retorna null) se o
    // endpoint não responder — o chamador trata e-mail ausente como um caso normal (RN: e-mail
    // privado no GitHub), não como erro 502.
    private String fetchPrimaryVerifiedEmail(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            var response = restTemplate.exchange(EMAILS_URL, HttpMethod.GET, request, String.class);
            com.fasterxml.jackson.databind.JsonNode emails = objectMapper.readTree(response.getBody());

            String firstVerified = null;
            for (com.fasterxml.jackson.databind.JsonNode entry : emails) {
                boolean verified = entry.path("verified").asBoolean(false);
                if (!verified) {
                    continue;
                }
                if (entry.path("primary").asBoolean(false)) {
                    return entry.path("email").asText(null);
                }
                if (firstVerified == null) {
                    firstVerified = entry.path("email").asText(null);
                }
            }
            return firstVerified;
        } catch (Exception e) {
            return null;
        }
    }
}
