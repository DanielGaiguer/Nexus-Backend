package com.main.nexus.dto;

public record UserSummaryDTO(
        Long id,
        String name,
        String email,
        String type,
        Boolean active,
        String profilePhotoUrl,
        // Id da própria linha professional/company (PK independente, gerada por
        // IDENTITY própria — não é o mesmo id sequencial do usuário/login, já
        // que professional/company são criados em ordens diferentes). Nulo pra
        // ADMIN. É esse id que as rotas /admin/professional/{id} e
        // /admin/company/{id} esperam — nunca o `id` (User) acima.
        Long entityId
) {}
