package com.umc.pediupatrao.dto;

import java.time.LocalDate;

public record IndicadoresDashboard(
        LocalDate dataInicio,
        LocalDate dataFim,
        long totalPedidos,
        long pedidosValidos,
        long pedidosCancelados,
        double faturamento,
        double ticketMedio,
        double taxaCancelamento) {
}
