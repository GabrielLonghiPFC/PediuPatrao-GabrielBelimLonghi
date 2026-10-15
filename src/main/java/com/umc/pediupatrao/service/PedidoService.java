package com.umc.pediupatrao.service;

import com.umc.pediupatrao.config.ControlePerfil;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PedidoService {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ControlePerfil controlePerfil;

    public Pedido criarPedido(Pedido pedido) {
        controlePerfil.verificar("GERENTE", "ATENDENTE");
        return pedidoRepository.save(pedido);
    }

    public List<Pedido> listarPedidos() {
        controlePerfil.verificar("ADMIN", "GERENTE", "ATENDENTE");
        return pedidoRepository.findAll();
    }

    public Pedido atualizarStatus(String id, String status) {
        controlePerfil.verificar("GERENTE");
        Pedido pedido = pedidoRepository.findById(id).orElseThrow();
        pedido.setStatus(status);
        return pedidoRepository.save(pedido);
    }

}
