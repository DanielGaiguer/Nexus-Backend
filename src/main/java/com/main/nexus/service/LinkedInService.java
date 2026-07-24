package com.main.nexus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Integração com "Sign In with LinkedIn using OpenID Connect".
 * Observação: o endpoint /v2/userinfo do LinkedIn NÃO expõe a URL pública do
 * perfil (ex: linkedin.com/in/usuario) — apenas sub/name/email/picture. Por
 * isso o login/vínculo com LinkedIn serve para autenticar e provar a
 * identidade da conta; a URL do perfil continua sendo informada manualmente
 * pelo usuário no cadastro/edição de perfil.
 */
@Service
public class LinkedInService {

    private static final String AUTHORIZATION_URL = "https://www.linkedin.com/oauth/v2/authorization";
    private static final String TOKEN_URL = "https://www.linkedin.com/oauth/v2/accessToken";
    private static final String USERINFO_URL = "https://api.linkedin.com/v2/userinfo";
    private static final String SCOPE = "openid profile email";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${linkedin.client-id}")
    private String clientId;

    @Value("${linkedin.client-secret}")
    private String clientSecret;

    @Value("${linkedin.redirect-uri}")
    private String redirectUri;

    public record LinkedInUserInfo(String sub, String email, String name, String picture) {}

    public String buildAuthorizationUrl(String state) {
        return AUTHORIZATION_URL
                + "?response_type=code"
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(SCOPE, StandardCharsets.UTF_8);
    }

    public LinkedInUserInfo exchangeCodeForUserInfo(String code) {
        String accessToken = fetchAccessToken(code);
        return fetchUserInfo(accessToken);
    }

    private String fetchAccessToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        JsonNode tokenNode;
        try {
            String response = restTemplate.postForObject(TOKEN_URL, request, String.class);
            tokenNode = objectMapper.readTree(response);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "Failed to exchange authorization code with LinkedIn.", e);
        }

        String accessToken = tokenNode.path("access_token").asText(null);
        if (accessToken == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "LinkedIn did not return an access token.");
        }
        return accessToken;
    }

    private LinkedInUserInfo fetchUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        JsonNode userInfoNode;
        try {
            var response = restTemplate.exchange(USERINFO_URL, HttpMethod.GET, request, String.class);
            userInfoNode = objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "Failed to fetch LinkedIn user info.", e);
        }

        String sub = userInfoNode.path("sub").asText(null);
        if (sub == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "LinkedIn user info response is missing 'sub'.");
        }

        return new LinkedInUserInfo(
                sub,
                userInfoNode.path("email").asText(null),
                userInfoNode.path("name").asText(null),
                userInfoNode.path("picture").asText(null)
        );
    }
}
