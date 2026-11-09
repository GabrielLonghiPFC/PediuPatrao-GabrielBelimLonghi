package com.umc.pediupatrao.dto;

import java.util.List;

public record VendasPorPeriodo(String agrupamento, List<VendaPeriodo> series) {
}
