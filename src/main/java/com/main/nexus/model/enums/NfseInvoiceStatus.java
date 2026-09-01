package com.main.nexus.model.enums;

// Estado de uma NFS-e de comissao (camada financeira, Prompt 6). Ver NfseInvoice / NfseService.
public enum NfseInvoiceStatus {
    // Criada, aguardando a 1a tentativa de emissao (ou dados fiscais incompletos).
    PENDING,
    // Enviada ao eNotas, aguardando o resultado (webhook ou consulta).
    PROCESSING,
    // Autorizada pela prefeitura -- linkPdf/linkXml/numero preenchidos.
    ISSUED,
    // Recusada pela prefeitura ou pelo eNotas (dado fiscal invalido, etc.) ->
    // fila de pendencias do Admin. Nao trava o restante do fluxo financeiro.
    FAILED,
    // Cancelada (ex.: comissao estornada).
    CANCELED
}
