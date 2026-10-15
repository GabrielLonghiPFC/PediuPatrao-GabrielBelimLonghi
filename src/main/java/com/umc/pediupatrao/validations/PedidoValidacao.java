package com.umc.pediupatrao.validations;

import com.umc.pediupatrao.entity.StatusPedido;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PedidoValidacao {

    private static final Map<String, Set<String>> TRANSICOES = Map.of(
            StatusPedido.RECEBIDO, Set.of(StatusPedido.EM_PREPARACAO),
            StatusPedido.EM_PREPARACAO, Set.of(StatusPedido.PRONTO),
            StatusPedido.PRONTO, Set.of(StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.RETIRADO),
            StatusPedido.SAIU_PARA_ENTREGA, Set.of(StatusPedido.FINALIZADO),
            StatusPedido.RETIRADO, Set.of(StatusPedido.FINALIZADO)
    );

    private PedidoValidacao() {
    }

    // Padroniza o texto do status (inclusive valores antigos do banco)
    public static String lerStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status não informado.");
        }
        String s = status.trim().toUpperCase().replace(' ', '_');
        if ("EM_PREPARO".equals(s) || "EM_PREPARACAO".equals(s)) {
            return StatusPedido.EM_PREPARACAO;
        }
        if ("ENTREGUE".equals(s)) {
            return StatusPedido.FINALIZADO;
        }
        return s;
    }

    // Verifica se a mudança de status segue o fluxo permitido
    public static boolean checarMudancaStatus(String statusAtual, String novoStatus) {
        String atual = lerStatus(statusAtual);
        String novo = lerStatus(novoStatus);
        if (StatusPedido.CANCELADO.equals(atual) || StatusPedido.FINALIZADO.equals(atual)) {
            return false;
        }
        Set<String> destinos = TRANSICOES.get(atual);
        return destinos != null && destinos.contains(novo);
    }

    // Indica se nesta mudança deve registrar data/hora e responsável pela saída
    public static boolean validarSaidaPedido(String novoStatus) {
        String novo = lerStatus(novoStatus);
        return StatusPedido.SAIU_PARA_ENTREGA.equals(novo) || StatusPedido.RETIRADO.equals(novo);
    }

    // Próximos status que o pedido pode receber (para a tela do gerente)
    public static List<String> proximosStatus(String statusAtual) {
        if (statusAtual == null || statusAtual.isBlank()) {
            return List.of(StatusPedido.EM_PREPARACAO);
        }
        try {
            String atual = lerStatus(statusAtual);
            if (StatusPedido.CANCELADO.equals(atual) || StatusPedido.FINALIZADO.equals(atual)) {
                return Collections.emptyList();
            }
            Set<String> destinos = TRANSICOES.get(atual);
            if (destinos == null || destinos.isEmpty()) {
                return Collections.emptyList();
            }
            return destinos.stream().sorted().toList();
        } catch (IllegalArgumentException e) {
            return Collections.emptyList();
        }
    }

    // Bloqueia cancelamento após saída, retirada ou finalização (e se já cancelado)
    public static boolean naoPodeCancelar(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        try {
            String s = lerStatus(status);
            return StatusPedido.CANCELADO.equals(s)
                    || StatusPedido.FINALIZADO.equals(s)
                    || StatusPedido.SAIU_PARA_ENTREGA.equals(s)
                    || StatusPedido.RETIRADO.equals(s);
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    // Bloqueia desconto após saída para entrega, retirada ou finalização
    public static boolean naoPodeDesconto(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String s = lerStatus(status);
        return StatusPedido.SAIU_PARA_ENTREGA.equals(s)
                || StatusPedido.RETIRADO.equals(s)
                || StatusPedido.FINALIZADO.equals(s);
    }
}
