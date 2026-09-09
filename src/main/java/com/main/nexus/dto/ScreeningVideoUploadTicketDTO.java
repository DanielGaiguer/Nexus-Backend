package com.main.nexus.dto;

// Autorizacao de upload de UM video, devolvida pelo backend depois dos guards (dono da
// tentativa, prazo aberto, consentimento de gravacao registrado). O browser sobe o arquivo
// DIRETO pro Supabase com `uploadUrl` -- o backend nunca ve os bytes.
//
// `objectUrl` e o que o cliente devolve na confirmacao; ele nao abre nada sozinho (bucket
// privado), so identifica o objeto. `maxSizeBytes` viaja junto pra o front poder barrar antes
// de gastar upload -- mas quem decide de verdade e o backend, na confirmacao.
public record ScreeningVideoUploadTicketDTO(
        Long questionId,
        String uploadUrl,
        String token,
        String objectUrl,
        Long maxSizeBytes,
        Integer expiresInSeconds
) {}
