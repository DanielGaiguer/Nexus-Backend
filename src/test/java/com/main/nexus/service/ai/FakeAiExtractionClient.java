package com.main.nexus.service.ai;

import com.main.nexus.dto.AiExtractionResponseDTO;

// Dublê de teste para AiExtractionClient — nenhuma chamada de rede, resposta determinística
// configurada pelo próprio teste via willReturn()/willThrow(). Existe justamente para testar
// ProjectAiExtractionService (normalização, matching de skills, rate limit) sem gastar
// tokens/dinheiro com o Gemini a cada rodada de build.
public class FakeAiExtractionClient implements AiExtractionClient {

    private AiExtractionResponseDTO nextResponse;
    private RuntimeException nextError;

    public void willReturn(AiExtractionResponseDTO response) {
        this.nextResponse = response;
        this.nextError = null;
    }

    public void willThrow(RuntimeException error) {
        this.nextError = error;
        this.nextResponse = null;
    }

    @Override
    public AiExtractionResponseDTO extract(String rawText) {
        if (nextError != null) {
            throw nextError;
        }
        return nextResponse;
    }
}
