package com.umc.pediupatrao.validations;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.service.AuditoriaService;
import java.util.function.Function;

public final class ClienteAuditoria {

    private ClienteAuditoria() {
    }

    public static void registrar(AuditoriaService auditoriaService, Cliente anterior, Cliente atual, boolean inclusao) {
        String operacao = inclusao ? "INCLUSAO DE CLIENTE" : "ALTERACAO DE CLIENTE";
        String id = atual.getId();
        if (inclusao) {
            auditoriaService.registrar(
                    operacao,
                    "CLIENTE",
                    id,
                    "nome",
                    "",
                    vazioSeNulo(atual.getNome()),
                    null);
            return;
        }
        registrarCampo(auditoriaService, operacao, id, "nome", anterior, Cliente::getNome, atual, false);
        registrarCampo(auditoriaService, operacao, id, "telefone", anterior, Cliente::getTelefone, atual, inclusao);
        registrarCampo(auditoriaService, operacao, id, "logradouro", anterior, Cliente::getLogradouro, atual, inclusao);
        registrarCampo(auditoriaService, operacao, id, "numero", anterior, Cliente::getNumero, atual, inclusao);
        registrarCampo(auditoriaService, operacao, id, "complemento", anterior, Cliente::getComplemento, atual, inclusao);
        registrarCampo(auditoriaService, operacao, id, "bairro", anterior, Cliente::getBairro, atual, inclusao);
        registrarCampo(auditoriaService, operacao, id, "cidade", anterior, Cliente::getCidade, atual, inclusao);
        registrarCampo(auditoriaService, operacao, id, "estado", anterior, Cliente::getEstado, atual, inclusao);
        registrarCampo(auditoriaService, operacao, id, "cep", anterior, Cliente::getCep, atual, false);
    }

    private static void registrarCampo(AuditoriaService auditoriaService, String operacao, String entidadeId,
            String campo, Cliente anterior, Function<Cliente, String> leitor, Cliente atual, boolean inclusao) {
        String valorAnterior = anterior == null ? "" : vazioSeNulo(leitor.apply(anterior));
        String valorNovo = vazioSeNulo(leitor.apply(atual));
        if (inclusao) {
            if (valorNovo.isBlank()) {
                return;
            }
        } else if (valorAnterior.equals(valorNovo)) {
            return;
        }
        auditoriaService.registrar(operacao, "CLIENTE", entidadeId, campo, valorAnterior, valorNovo, null);
    }

    public static void registrarExclusao(AuditoriaService auditoriaService, Cliente cliente) {
        auditoriaService.registrar(
                "EXCLUSAO DE CLIENTE",
                "CLIENTE",
                cliente.getId(),
                "nome",
                vazioSeNulo(cliente.getNome()),
                "",
                null);
    }

    private static String vazioSeNulo(String valor) {
        return valor == null ? "" : valor;
    }
}
