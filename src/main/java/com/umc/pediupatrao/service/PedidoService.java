package com.umc.pediupatrao.service;

import com.umc.pediupatrao.config.ControlePerfil;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.validations.PedidoValidacao;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PedidoService {

    private static final double DESCONTO_MAXIMO_PERCENTUAL = 20.0;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ControlePerfil controlePerfil;

    @Autowired
    private AuditoriaService auditoriaService;

    public Pedido criarPedido(Pedido pedido) {
        controlePerfil.verificar("GERENTE", "ATENDENTE");

        if (pedido.getClienteId() == null || !clienteRepository.existsById(pedido.getClienteId())) {
            throw new IllegalArgumentException("Cliente não encontrado.");
        }
        if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um item no pedido.");
        }

        List<ItemPedido> itens = montarItens(pedido.getItens());
        double subtotal = itens.stream().mapToDouble(ItemPedido::getSubtotal).sum();

        pedido.setItens(itens);
        pedido.setDescontoPercentual(0.0);
        pedido.setValorTotal(calcularTotal(subtotal, 0.0));
        pedido.setResponsavelRegistro(controlePerfil.usuarioLogado());
        pedido.setDataHoraEntrada(LocalDateTime.now());
        pedido.setStatus(StatusPedido.RECEBIDO);
        if (pedido.getId() == null || pedido.getId().isBlank()) {
            pedido.setId(UUID.randomUUID().toString());
        }

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(
                "INCLUSAO DE PEDIDO",
                "PEDIDO",
                salvo.getId(),
                "status",
                "",
                salvo.getStatus(),
                null);
        return salvo;
    }

    public List<Pedido> listarPedidos() {
        controlePerfil.verificar("ADMIN", "GERENTE", "ATENDENTE");
        return pedidoRepository.findAll();
    }

    public Optional<Pedido> buscarPorId(String id) {
        controlePerfil.verificar("ADMIN", "GERENTE", "ATENDENTE");
        return pedidoRepository.findById(id);
    }

    public Pedido atualizarStatus(String id, String status) {
        controlePerfil.verificar("GERENTE");
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));

        String statusAtual = pedido.getStatus() != null ? pedido.getStatus() : StatusPedido.RECEBIDO;
        String novoStatus;
        try {
            novoStatus = PedidoValidacao.lerStatus(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Status inválido.");
        }

        if (StatusPedido.CANCELADO.equals(novoStatus)) {
            throw new IllegalArgumentException("Use a operação de cancelamento para cancelar o pedido.");
        }

        if (StatusPedido.CANCELADO.equals(PedidoValidacao.lerStatus(statusAtual))) {
            throw new IllegalArgumentException("Pedido cancelado não pode ter o status alterado.");
        }

        if (!PedidoValidacao.checarMudancaStatus(statusAtual, novoStatus)) {
            throw new IllegalArgumentException(
                    "Transição inválida: de \"" + statusAtual + "\" para \"" + novoStatus + "\".");
        }

        if (PedidoValidacao.validarSaidaPedido(novoStatus)) {
            pedido.setDataHoraSaida(LocalDateTime.now());
            pedido.setResponsavelSaida(controlePerfil.usuarioLogado());
        }

        pedido.setStatus(novoStatus);
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(
                "MUDANCA DE STATUS DE PEDIDO",
                "PEDIDO",
                salvo.getId(),
                "status",
                statusAtual,
                novoStatus,
                null);
        return salvo;
    }

    public Pedido aplicarDesconto(String id, Double descontoPercentual) {
        controlePerfil.verificar("GERENTE");
        if (descontoPercentual == null || descontoPercentual < 0 || descontoPercentual > DESCONTO_MAXIMO_PERCENTUAL) {
            throw new IllegalArgumentException("Desconto deve estar entre 0 e 20%.");
        }
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
        if (StatusPedido.CANCELADO.equals(pedido.getStatus())) {
            throw new IllegalArgumentException("Não é possível aplicar desconto em pedido cancelado.");
        }
        if (PedidoValidacao.naoPodeDesconto(pedido.getStatus())) {
            throw new IllegalArgumentException("Não é possível aplicar desconto após saída para entrega ou retirada.");
        }
        if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
            throw new IllegalArgumentException("Pedido sem itens.");
        }
        double subtotal = pedido.getItens().stream().mapToDouble(ItemPedido::getSubtotal).sum();
        Double descontoAnterior = pedido.getDescontoPercentual();
        Double totalAnterior = pedido.getValorTotal();
        pedido.setDescontoPercentual(descontoPercentual);
        pedido.setValorTotal(calcularTotal(subtotal, descontoPercentual));
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(
                "APLICACAO DE DESCONTO",
                "PEDIDO",
                salvo.getId(),
                "descontoPercentual",
                descontoAnterior != null ? descontoAnterior.toString() : "0",
                descontoPercentual.toString(),
                null);
        auditoriaService.registrar(
                "APLICACAO DE DESCONTO",
                "PEDIDO",
                salvo.getId(),
                "valorTotal",
                totalAnterior != null ? totalAnterior.toString() : "",
                salvo.getValorTotal().toString(),
                null);
        return salvo;
    }

    public Pedido cancelarPedido(String id, String justificativa) {
        controlePerfil.verificar("GERENTE");
        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("Informe a justificativa do cancelamento.");
        }
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
        if (PedidoValidacao.naoPodeCancelar(pedido.getStatus())) {
            throw new IllegalArgumentException("Não é possível cancelar este pedido.");
        }
        String statusAnterior = pedido.getStatus();
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setJustificativaCancelamento(justificativa.trim());
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(
                "CANCELAMENTO DE PEDIDO",
                "PEDIDO",
                salvo.getId(),
                "status",
                statusAnterior,
                StatusPedido.CANCELADO,
                justificativa.trim());
        return salvo;
    }

    private List<ItemPedido> montarItens(List<ItemPedido> itensInformados) {
        List<ItemPedido> itens = new ArrayList<>();
        for (ItemPedido informado : itensInformados) {
            if (informado.getQuantidade() <= 0) {
                throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
            }
            Produto produto = produtoRepository.findById(informado.getProdutoId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
            if (Boolean.FALSE.equals(produto.getAtivo())) {
                throw new IllegalArgumentException("Produto inativo: " + produto.getNome());
            }

            ItemPedido item = new ItemPedido();
            item.setProdutoId(produto.getId());
            item.setNomeProduto(produto.getNome());
            item.setQuantidade(informado.getQuantidade());
            item.setPrecoUnitario(produto.getPreco());
            item.setSubtotal(produto.getPreco() * informado.getQuantidade());
            itens.add(item);
        }
        return itens;
    }

    private double calcularTotal(double subtotal, double descontoPercentual) {
        return subtotal - (subtotal * descontoPercentual / 100);
    }
}
