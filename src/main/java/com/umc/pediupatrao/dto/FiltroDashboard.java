package com.umc.pediupatrao.dto;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record FiltroDashboard(LocalDate dataInicio, LocalDate dataFim, String status, String categoria) {

    public long dias() {
        return ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
    }
}
