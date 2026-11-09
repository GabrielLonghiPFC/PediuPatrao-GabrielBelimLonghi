package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.ErroApi;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = DashboardController.class)
public class DashboardExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DashboardExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroApi> parametroInvalido(IllegalArgumentException e, HttpServletRequest request) {
        return erro(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroApi> tipoInvalido(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String mensagem = "Parâmetro '" + e.getName() + "' inválido: '" + e.getValue() + "'.";
        if (LocalDate.class.equals(e.getRequiredType())) {
            mensagem += " Use o formato yyyy-MM-dd.";
        } else if (Integer.class.equals(e.getRequiredType())) {
            mensagem += " Informe um número inteiro.";
        }
        return erro(HttpStatus.BAD_REQUEST, mensagem, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroApi> semPermissao(AccessDeniedException e, HttpServletRequest request) {
        return erro(HttpStatus.FORBIDDEN, "Sem permissão.", request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErroApi> bancoIndisponivel(DataAccessException e, HttpServletRequest request) {
        log.error("Falha ao consultar o banco de dados para o dashboard", e);
        return erro(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível consultar o banco de dados. Tente novamente.",
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroApi> erroInesperado(Exception e, HttpServletRequest request) {
        log.error("Erro inesperado no dashboard", e);
        return erro(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao processar a consulta.", request);
    }

    private ResponseEntity<ErroApi> erro(HttpStatus status, String mensagem, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ErroApi(LocalDateTime.now(), status.value(), mensagem, request.getRequestURI()));
    }
}
