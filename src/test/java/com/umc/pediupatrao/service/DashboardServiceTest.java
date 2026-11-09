package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;

import com.umc.pediupatrao.config.ControlePerfil;
import com.umc.pediupatrao.dto.FiltroDashboard;
import com.umc.pediupatrao.dto.IndicadoresDashboard;
import com.umc.pediupatrao.dto.PedidoRecente;
import com.umc.pediupatrao.dto.ProdutoRanking;
import com.umc.pediupatrao.dto.StatusPedidoResumo;
import com.umc.pediupatrao.dto.VendaCategoria;
import com.umc.pediupatrao.dto.VendasPorPeriodo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 3);

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private ControlePerfil controlePerfil;

    @InjectMocks
    private DashboardService dashboardService;

    private final List<Pedido> pedidos = new ArrayList<>();

    @BeforeEach
    void configurar() {
        lenient().when(produtoRepository.findAll()).thenReturn(List.of(
                produto("p1", "Pizza Calabresa", "Pizza", 50.0),
                produto("p2", "Refrigerante 2L", "Bebida", 10.0),
                produto("p3", "Esfiha de Carne", "Esfiha", 5.0)));

        lenient().when(pedidoRepository.buscarPorPeriodo(any(), any())).thenAnswer(inv -> pedidos.stream()
                .filter(p -> noPeriodo(p, inv.getArgument(0), inv.getArgument(1)))
                .toList());
        lenient().when(pedidoRepository.buscarPorPeriodoEStatus(any(), any(), any())).thenAnswer(inv -> {
            List<String> status = inv.getArgument(2);
            return pedidos.stream()
                    .filter(p -> noPeriodo(p, inv.getArgument(0), inv.getArgument(1)))
                    .filter(p -> status.contains(p.getStatus()))
                    .toList();
        });
    }

    // Filtros
    @Test
    void filtroSemDatasUsaUltimos30Dias() {
        FiltroDashboard filtro = dashboardService.montarFiltro(null, null, null, null);

        assertEquals(LocalDate.now(), filtro.dataFim());
        assertEquals(LocalDate.now().minusDays(29), filtro.dataInicio());
        assertEquals(30, filtro.dias());
    }

    @Test
    void filtroRejeitaDataInicialPosteriorAFinal() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> dashboardService.montarFiltro(FIM, INICIO, null, null));

        assertEquals("A data inicial não pode ser posterior à data final.", erro.getMessage());
    }

    @Test
    void filtroRejeitaPeriodoMaiorQue366Dias() {
        assertThrows(IllegalArgumentException.class,
                () -> dashboardService.montarFiltro(INICIO.minusDays(400), INICIO, null, null));
    }

    @Test
    void filtroRejeitaStatusInvalido() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> dashboardService.montarFiltro(INICIO, FIM, "ENTREGANDO", null));

        assertEquals("Status inválido: ENTREGANDO.", erro.getMessage());
    }

    @Test
    void filtroPadronizaStatusAntigoECategoria() {
        FiltroDashboard filtro = dashboardService.montarFiltro(INICIO, FIM, "em preparo", "pizza");

        assertEquals(StatusPedido.EM_PREPARACAO, filtro.status());
        assertEquals("Pizza", filtro.categoria());
    }

    @Test
    void filtroRejeitaCategoriaInexistente() {
        assertThrows(IllegalArgumentException.class,
                () -> dashboardService.montarFiltro(INICIO, FIM, null, "Sobremesa"));
    }

    // Indicadores
    @Test
    void indicadoresIgnoramCanceladosNoFaturamento() {
        pedidos.add(pedido(INICIO.atTime(19, 0), StatusPedido.FINALIZADO, 0, item("p1", 2)));
        pedidos.add(pedido(INICIO.atTime(20, 0), StatusPedido.FINALIZADO, 10, item("p2", 5)));
        pedidos.add(pedido(FIM.atTime(21, 0), StatusPedido.CANCELADO, 0, item("p1", 1)));

        IndicadoresDashboard ind = dashboardService.indicadores(filtro());

        assertEquals(3, ind.totalPedidos());
        assertEquals(2, ind.pedidosValidos());
        assertEquals(1, ind.pedidosCancelados());
        assertEquals(145.0, ind.faturamento());
        assertEquals(72.5, ind.ticketMedio());
        assertEquals(33.33, ind.taxaCancelamento());
    }

    @Test
    void indicadoresSemPedidosRetornamZeroSemErro() {
        IndicadoresDashboard ind = dashboardService.indicadores(filtro());

        assertEquals(0, ind.totalPedidos());
        assertEquals(0.0, ind.faturamento());
        assertEquals(0.0, ind.ticketMedio());
        assertEquals(0.0, ind.taxaCancelamento());
    }

    @Test
    void indicadoresRecalculamTotalQuandoValorTotalNaoFoiGravado() {
        Pedido antigo = pedido(INICIO.atTime(12, 0), StatusPedido.FINALIZADO, 0, item("p1", 1));
        antigo.setValorTotal(null);
        antigo.getItens().get(0).setSubtotal(null);
        pedidos.add(antigo);

        assertEquals(50.0, dashboardService.indicadores(filtro()).faturamento());
    }

    @Test
    void usuarioSemPerfilNaoConsultaIndicadores() {
        doThrow(new AccessDeniedException("Sem permissão.")).when(controlePerfil).verificar("ADMIN", "GERENTE");

        assertThrows(AccessDeniedException.class, () -> dashboardService.indicadores(filtro()));
    }

    // Vendas
    @Test
    void vendasPorDiaPreenchemDiasSemPedidos() {
        pedidos.add(pedido(INICIO.atTime(19, 0), StatusPedido.FINALIZADO, 0, item("p1", 1)));
        pedidos.add(pedido(FIM.atTime(19, 0), StatusPedido.CANCELADO, 0, item("p1", 1)));

        VendasPorPeriodo vendas = dashboardService.vendasPorPeriodo(filtro(), null);

        assertEquals("DIA", vendas.agrupamento());
        assertEquals(3, vendas.series().size());
        assertEquals("01/11", vendas.series().get(0).rotulo());
        assertEquals(50.0, vendas.series().get(0).faturamento());
        assertEquals(0, vendas.series().get(1).pedidos());
        assertEquals(1, vendas.series().get(2).cancelados());
        assertEquals(0.0, vendas.series().get(2).faturamento());
    }

    @Test
    void vendasAgrupadasPorMes() {
        FiltroDashboard filtro = new FiltroDashboard(LocalDate.of(2026, 9, 15), FIM, null, null);
        pedidos.add(pedido(LocalDateTime.of(2026, 9, 20, 19, 0), StatusPedido.FINALIZADO, 0, item("p1", 1)));
        pedidos.add(pedido(LocalDateTime.of(2026, 10, 5, 19, 0), StatusPedido.FINALIZADO, 0, item("p1", 2)));

        VendasPorPeriodo vendas = dashboardService.vendasPorPeriodo(filtro, "mes");

        assertEquals("MES", vendas.agrupamento());
        assertEquals(List.of("09/2026", "10/2026", "11/2026"), vendas.series().stream().map(v -> v.rotulo()).toList());
        assertEquals(100.0, vendas.series().get(1).faturamento());
    }

    @Test
    void vendasRejeitamAgrupamentoInvalido() {
        assertThrows(IllegalArgumentException.class, () -> dashboardService.vendasPorPeriodo(filtro(), "ANO"));
    }

    @Test
    void vendasPorCategoriaAplicamDescontoDoPedido() {
        pedidos.add(pedido(INICIO.atTime(19, 0), StatusPedido.FINALIZADO, 10, item("p1", 2), item("p2", 1)));

        List<VendaCategoria> categorias = dashboardService.vendasPorCategoria(filtro());

        assertEquals("Pizza", categorias.get(0).categoria());
        assertEquals(90.0, categorias.get(0).faturamento());
        assertEquals(90.91, categorias.get(0).percentual());
        assertEquals("Bebida", categorias.get(1).categoria());
        assertEquals(9.0, categorias.get(1).faturamento());
    }

    // Situação dos pedidos
    @Test
    void statusContaTodosOsStatusEPadronizaValoresAntigos() {
        pedidos.add(pedido(INICIO.atTime(19, 0), "EM_PREPARO", 0, item("p1", 1)));
        pedidos.add(pedido(INICIO.atTime(19, 5), StatusPedido.EM_PREPARACAO, 0, item("p1", 1)));
        pedidos.add(pedido(INICIO.atTime(19, 10), "ENTREGUE", 0, item("p1", 1)));
        pedidos.add(pedido(INICIO.atTime(19, 15), StatusPedido.CANCELADO, 0, item("p1", 1)));

        List<StatusPedidoResumo> status = dashboardService.pedidosPorStatus(filtro());

        assertEquals(DashboardService.STATUS_PEDIDO.size(), status.size());
        StatusPedidoResumo preparo = buscarStatus(status, StatusPedido.EM_PREPARACAO);
        assertEquals(2, preparo.quantidade());
        assertEquals(50.0, preparo.percentual());
        assertEquals(1, buscarStatus(status, StatusPedido.FINALIZADO).quantidade());
        assertEquals(0, buscarStatus(status, StatusPedido.PRONTO).quantidade());
    }

    @Test
    void filtroDeStatusConsultaTambemValoresAntigos() {
        pedidos.add(pedido(INICIO.atTime(19, 0), "ENTREGUE", 0, item("p1", 1)));
        pedidos.add(pedido(INICIO.atTime(19, 5), StatusPedido.FINALIZADO, 0, item("p1", 1)));
        pedidos.add(pedido(INICIO.atTime(19, 10), StatusPedido.RECEBIDO, 0, item("p1", 1)));

        FiltroDashboard filtro = dashboardService.montarFiltro(INICIO, FIM, "FINALIZADO", null);

        assertEquals(2, dashboardService.indicadores(filtro).totalPedidos());
    }

    @Test
    void pedidosRecentesVemDoMaisNovoParaOMaisAntigo() {
        Cliente cliente = new Cliente();
        cliente.setId("c1");
        cliente.setNome("Ana Souza");
        lenient().when(clienteRepository.findAllById(any())).thenReturn(List.of(cliente));
        pedidos.add(pedido(INICIO.atTime(10, 0), StatusPedido.FINALIZADO, 0, item("p1", 1)));
        pedidos.add(pedido(FIM.atTime(22, 0), StatusPedido.RECEBIDO, 0, item("p2", 3)));
        pedidos.add(pedido(INICIO.atTime(15, 0), StatusPedido.CANCELADO, 0, item("p3", 1)));

        List<PedidoRecente> recentes = dashboardService.pedidosRecentes(filtro(), 2);

        assertEquals(2, recentes.size());
        assertEquals(FIM.atTime(22, 0), recentes.get(0).dataHoraEntrada());
        assertEquals("Ana Souza", recentes.get(0).cliente());
        assertEquals(30.0, recentes.get(0).valorTotal());
        assertEquals(StatusPedido.CANCELADO, recentes.get(1).status());
    }

    // Produtos mais vendidos
    @Test
    void rankingOrdenaPorQuantidadeEIgnoraCancelados() {
        pedidos.add(pedido(INICIO.atTime(19, 0), StatusPedido.FINALIZADO, 0, item("p1", 1), item("p2", 4)));
        pedidos.add(pedido(INICIO.atTime(20, 0), StatusPedido.FINALIZADO, 0, item("p1", 2), item("p3", 1)));
        pedidos.add(pedido(FIM.atTime(20, 0), StatusPedido.CANCELADO, 0, item("p3", 10)));

        List<ProdutoRanking> ranking = dashboardService.produtosMaisVendidos(filtro(), null);

        assertEquals(3, ranking.size());
        assertEquals("Refrigerante 2L", ranking.get(0).nome());
        assertEquals(4, ranking.get(0).quantidade());
        assertEquals(50.0, ranking.get(0).participacao());
        assertEquals("Pizza Calabresa", ranking.get(1).nome());
        assertEquals(150.0, ranking.get(1).faturamento());
        assertEquals("Pizza", ranking.get(1).categoria());
        assertEquals(1, ranking.get(2).quantidade());
        assertEquals(2, dashboardService.produtosMaisVendidos(filtro(), 2).size());
    }

    @Test
    void rankingRejeitaLimiteForaDoIntervalo() {
        assertThrows(IllegalArgumentException.class, () -> dashboardService.produtosMaisVendidos(filtro(), 0));
        assertThrows(IllegalArgumentException.class, () -> dashboardService.produtosMaisVendidos(filtro(), 51));
    }

    @Test
    void filtroDeCategoriaConsideraSomenteItensDaCategoria() {
        pedidos.add(pedido(INICIO.atTime(19, 0), StatusPedido.FINALIZADO, 10, item("p1", 1), item("p2", 2)));
        pedidos.add(pedido(INICIO.atTime(20, 0), StatusPedido.FINALIZADO, 0, item("p3", 4)));

        FiltroDashboard filtro = dashboardService.montarFiltro(INICIO, FIM, null, "Bebida");
        IndicadoresDashboard ind = dashboardService.indicadores(filtro);

        assertEquals(1, ind.pedidosValidos());
        assertEquals(18.0, ind.faturamento());
        assertEquals(1, dashboardService.produtosMaisVendidos(filtro, null).size());
    }

    // Auxiliares
    private FiltroDashboard filtro() {
        return new FiltroDashboard(INICIO, FIM, null, null);
    }

    private static boolean noPeriodo(Pedido p, LocalDateTime inicio, LocalDateTime fim) {
        return !p.getDataHoraEntrada().isBefore(inicio) && p.getDataHoraEntrada().isBefore(fim);
    }

    private static StatusPedidoResumo buscarStatus(List<StatusPedidoResumo> lista, String status) {
        return lista.stream().filter(s -> s.status().equals(status)).findFirst().orElseThrow();
    }

    private Produto produto(String id, String nome, String tipo, double preco) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(preco);
        produto.setAtivo(true);
        return produto;
    }

    private ItemPedido item(String produtoId, int quantidade) {
        Produto produto = produtoRepository.findAll().stream()
                .filter(p -> p.getId().equals(produtoId))
                .findFirst()
                .orElseThrow();
        ItemPedido item = new ItemPedido();
        item.setProdutoId(produtoId);
        item.setNomeProduto(produto.getNome());
        item.setQuantidade(quantidade);
        item.setPrecoUnitario(produto.getPreco());
        item.setSubtotal(produto.getPreco() * quantidade);
        return item;
    }

    private Pedido pedido(LocalDateTime entrada, String status, double desconto, ItemPedido... itens) {
        Pedido pedido = new Pedido();
        pedido.setId(UUID.randomUUID().toString());
        pedido.setClienteId("c1");
        pedido.setDataHoraEntrada(entrada);
        pedido.setStatus(status);
        pedido.setItens(new ArrayList<>(List.of(itens)));
        pedido.setDescontoPercentual(desconto);
        double subtotal = pedido.getItens().stream().mapToDouble(ItemPedido::getSubtotal).sum();
        pedido.setValorTotal(subtotal - (subtotal * desconto / 100));
        pedido.setResponsavelRegistro("gerente");
        return pedido;
    }
}
