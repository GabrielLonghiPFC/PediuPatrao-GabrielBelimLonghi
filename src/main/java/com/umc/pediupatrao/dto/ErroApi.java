package com.umc.pediupatrao.dto;

import java.time.LocalDateTime;

public record ErroApi(LocalDateTime timestamp, int status, String erro, String caminho) {
}
