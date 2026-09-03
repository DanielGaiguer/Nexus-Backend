package com.main.nexus.dto;

// Corpo do POST do Admin para publicar uma nova versao de um documento legal.
// A versao e o "ativo" sao decididos pelo servidor (max+1, flip da anterior);
// o cliente so manda o texto novo e o resumo das mudancas.
public record PublishLegalDocumentDTO(
        String content,
        String summaryOfChanges
) {}
