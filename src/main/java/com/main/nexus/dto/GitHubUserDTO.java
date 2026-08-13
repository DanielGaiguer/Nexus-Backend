package com.main.nexus.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// Espelha a resposta de GET https://api.github.com/user. O id chega como número no JSON do GitHub, mas e guardado como String
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubUserDTO(
        String id,
        String login,
        String name,
        String email,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("html_url") String htmlUrl
) {}
