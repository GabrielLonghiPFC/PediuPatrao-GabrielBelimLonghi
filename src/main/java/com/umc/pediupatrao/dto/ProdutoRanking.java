package com.umc.pediupatrao.dto;

public record ProdutoRanking(
        int posicao,
        String produtoId,
        String nome,
        String categoria,
        long quantidade,
        double faturamento,
        double participacao) {
}
