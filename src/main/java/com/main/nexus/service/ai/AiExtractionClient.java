package com.main.nexus.service.ai;

import com.main.nexus.dto.AiExtractionResponseDTO;

// Ponto de troca de provedor de IA. A implementação real (GeminiExtractionClient) chama o
// Gemini via Spring AI; testes injetam um fake determinístico (ver FakeAiExtractionClient em
// src/test) para não gastar chamadas reais a cada rodada.
//
// Contrato: extract() nunca lança para "IA discordou/teve baixa confiança" — isso vira campos
// null e entradas em lowConfidenceFields. Só lança (RuntimeException, tratada pelo chamador)
// quando a chamada em si falhou (timeout, resposta ilegível, provedor fora do ar).
public interface AiExtractionClient {

    AiExtractionResponseDTO extract(String rawText);
}
