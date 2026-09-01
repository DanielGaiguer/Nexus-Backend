package com.main.nexus.dto;

import java.math.BigDecimal;

// Situacao do contratante logado frente a comissao, para o indicador na area
// dele: quantas contratacoes gratuitas ja usou / quantas restam / se ja esta na
// faixa com comissao, e qual o percentual vigente. Puramente informativo --
// nenhuma cobranca acontece nesta etapa.
public record ContractorCommissionStatusDTO(
        int freeHiresLimit,
        int usedFreeHires,
        int freeHiresRemaining,
        boolean commissionApplies,
        BigDecimal currentPercentage
) {}
