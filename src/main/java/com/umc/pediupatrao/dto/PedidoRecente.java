package com.umc.pediupatrao.dto;

import java.time.LocalDateTime;

public record PedidoRecente(String id, String cliente, LocalDateTime dataHoraEntrada, String status, double valorTotal) {
}
