package com.umc.pediupatrao.service;

import com.umc.pediupatrao.config.ControlePerfil;
import com.umc.pediupatrao.dto.FiltroDashboard;
import com.umc.pediupatrao.dto.IndicadoresDashboard;
import com.umc.pediupatrao.dto.OpcoesFiltroDashboard;
import com.umc.pediupatrao.dto.PedidoRecente;
import com.umc.pediupatrao.dto.ProdutoRanking;
import com.umc.pediupatrao.dto.StatusPedidoResumo;
import com.umc.pediupatrao.dto.VendaCategoria;
import com.umc.pediupatrao.dto.VendaPeriodo;
import com.umc.pediupatrao.dto.VendasPorPeriodo;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.validations.PedidoValidacao;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    public static final int PERIODO_PADRAO_DIAS = 30;
    public static final int INTERVALO_MAXIMO_DIAS = 366;
    public static final int LIMITE_MAXIMO = 50;
    public static final String SEM_CATEGORIA = "Sem categoria";

    public static final List<String> STATUS_PEDIDO = List.of(
            StatusPedido.RECEBIDO,
            StatusPedido.EM_PREPARACAO,
            StatusPedido.PRONTO,
            StatusPedido.SAIU_PARA_ENTREGA,
            StatusPedido.RETIRADO,
            StatusPedido.FINALIZADO,
            StatusPedido.CANCELADO);
    public static final List<String> AGRUPAMENTOS = List.of("DIA", "SEMANA", "MES");

    private static final DateTimeFormatter ROTULO_DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter ROTULO_MES = DateTimeFormatter.ofPattern("MM/yyyy");

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;
    private final ControlePerfil controlePerfil;

    public DashboardService(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository,
            ClienteRepository clienteRepository, ControlePerfil controlePerfil) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.clienteRepository = clienteRepository;
        this.controlePerfil = controlePerfil;
    }

    private record PedidoConsiderado(Pedido pedido, String status, List<ItemPedido> itens,
            double descontoPercentual, double valor) {

        boolean cancelado() {
            return StatusPedido.CANCELADO.equals(status);
        }
    }

    // Filtros
    public FiltroDashboard montarFiltro(LocalDate dataInicio, LocalDate dataFim, String status, String categoria) {
        LocalDate fim = dataFim != null ? dataFim : LocalDate.now();
        LocalDate inicio = dataInicio != null ? dataInicio : fim.minusDays(PERIODO_PADRAO_DIAS - 1);

        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final.");
        }
        FiltroDashboard filtro = new FiltroDashboard(inicio, fim, validarStatus(status), validarCategoria(categoria));
        if (filtro.dias() > INTERVALO_MAXIMO_DIAS) {
            throw new IllegalArgumentException(
                    "O período consultado não pode ultrapassar " + INTERVALO_MAXIMO_DIAS + " dias.");
        }
        return filtro;
    }

    public OpcoesFiltroDashboard opcoesFiltro() {
        controlePerfil.verificar("ADMIN", "GERENTE");
        return new OpcoesFiltroDashboard(STATUS_PEDIDO, listarCategorias());
    }

    // Indicadores gerais
    public IndicadoresDashboard indicadores(FiltroDashboard filtro) {
        controlePerfil.verificar("ADMIN", "GERENTE");
        List<PedidoConsiderado> pedidos = consultar(filtro, produtosPorId());
        List<PedidoConsiderado> validos = validos(pedidos);
        long cancelados = pedidos.size() - validos.size();
        double faturamento = validos.stream().mapToDouble(PedidoConsiderado::valor).sum();

        return new IndicadoresDashboard(
                filtro.dataInicio(),
                filtro.dataFim(),
                pedidos.size(),
                validos.size(),
                cancelados,
                arredondar(faturamento),
                arredondar(validos.isEmpty() ? 0 : faturamento / validos.size()),
                percentual(cancelados, pedidos.size()));
    }

    // Análise de vendas
    public VendasPorPeriodo vendasPorPeriodo(FiltroDashboard filtro, String agrupamento) {
        controlePerfil.verificar("ADMIN", "GERENTE");
        String tipo = definirAgrupamento(filtro, agrupamento);
        List<PedidoConsiderado> pedidos = consultar(filtro, produtosPorId());

        Map<LocalDate, long[]> quantidades = new LinkedHashMap<>();
        Map<LocalDate, Double> valores = new HashMap<>();
        for (LocalDate d = inicioDoGrupo(filtro.dataInicio(), tipo); !d.isAfter(filtro.dataFim()); d = proximoGrupo(d, tipo)) {
            quantidades.put(d, new long[2]);
            valores.put(d, 0.0);
        }

        for (PedidoConsiderado p : pedidos) {
            LocalDate grupo = inicioDoGrupo(p.pedido().getDataHoraEntrada().toLocalDate(), tipo);
            long[] qtd = quantidades.get(grupo);
            if (p.cancelado()) {
                qtd[1]++;
            } else {
                qtd[0]++;
                valores.merge(grupo, p.valor(), Double::sum);
            }
        }

        List<VendaPeriodo> series = quantidades.entrySet().stream()
                .map(e -> new VendaPeriodo(rotulo(e.getKey(), tipo), e.getValue()[0],
                        arredondar(valores.get(e.getKey())), e.getValue()[1]))
                .toList();
        return new VendasPorPeriodo(tipo, series);
    }

    public List<VendaCategoria> vendasPorCategoria(FiltroDashboard filtro) {
        controlePerfil.verificar("ADMIN", "GERENTE");
        Map<String, Produto> produtos = produtosPorId();

        Map<String, Double> valores = new HashMap<>();
        for (PedidoConsiderado p : validos(consultar(filtro, produtos))) {
            for (ItemPedido item : p.itens()) {
                valores.merge(categoriaDoItem(item, produtos), valorComDesconto(item, p), Double::sum);
            }
        }

        double total = valores.values().stream().mapToDouble(Double::doubleValue).sum();
        return valores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> new VendaCategoria(e.getKey(), arredondar(e.getValue()), percentual(e.getValue(), total)))
                .toList();
    }

    // Situação dos pedidos
    public List<StatusPedidoResumo> pedidosPorStatus(FiltroDashboard filtro) {
        controlePerfil.verificar("ADMIN", "GERENTE");
        List<PedidoConsiderado> pedidos = consultar(filtro, produtosPorId());

        return STATUS_PEDIDO.stream()
                .map(status -> {
                    long quantidade = pedidos.stream().filter(p -> status.equals(p.status())).count();
                    return new StatusPedidoResumo(status, quantidade, percentual(quantidade, pedidos.size()));
                })
                .toList();
    }

    public List<PedidoRecente> pedidosRecentes(FiltroDashboard filtro, Integer limite) {
        controlePerfil.verificar("ADMIN", "GERENTE");
        List<PedidoConsiderado> recentes = consultar(filtro, produtosPorId()).stream()
                .sorted(Comparator.comparing((PedidoConsiderado p) -> p.pedido().getDataHoraEntrada()).reversed())
                .limit(validarLimite(limite, 10))
                .toList();

        Map<String, String> nomesClientes = new HashMap<>();
        clienteRepository.findAllById(recentes.stream()
                        .map(p -> p.pedido().getClienteId())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .forEach(c -> nomesClientes.put(c.getId(), c.getNome()));

        return recentes.stream()
                .map(p -> new PedidoRecente(
                        p.pedido().getId(),
                        nomesClientes.getOrDefault(p.pedido().getClienteId(), "Cliente não encontrado"),
                        p.pedido().getDataHoraEntrada(),
                        p.status(),
                        arredondar(p.valor())))
                .toList();
    }

    // Produtos mais vendidos
    public List<ProdutoRanking> produtosMaisVendidos(FiltroDashboard filtro, Integer limite) {
        controlePerfil.verificar("ADMIN", "GERENTE");
        Map<String, Produto> produtos = produtosPorId();

        Map<String, ItemPedido> referencia = new LinkedHashMap<>();
        Map<String, Long> quantidades = new HashMap<>();
        Map<String, Double> valores = new HashMap<>();
        for (PedidoConsiderado p : validos(consultar(filtro, produtos))) {
            for (ItemPedido item : p.itens()) {
                String chave = item.getProdutoId() != null ? item.getProdutoId() : item.getNomeProduto();
                if (chave == null) {
                    continue;
                }
                referencia.putIfAbsent(chave, item);
                quantidades.merge(chave, (long) item.getQuantidade(), Long::sum);
                valores.merge(chave, valorComDesconto(item, p), Double::sum);
            }
        }

        double totalQuantidade = quantidades.values().stream().mapToLong(Long::longValue).sum();
        List<String> chaves = referencia.keySet().stream()
                .sorted(Comparator.comparing((String chave) -> quantidades.get(chave)).reversed()
                        .thenComparing(Comparator.comparing((String chave) -> valores.get(chave)).reversed()))
                .limit(validarLimite(limite, 5))
                .toList();

        List<ProdutoRanking> ranking = new ArrayList<>();
        for (int i = 0; i < chaves.size(); i++) {
            String chave = chaves.get(i);
            ItemPedido item = referencia.get(chave);
            ranking.add(new ProdutoRanking(
                    i + 1,
                    item.getProdutoId(),
                    nomeProduto(item, produtos),
                    categoriaDoItem(item, produtos),
                    quantidades.get(chave),
                    arredondar(valores.get(chave)),
                    percentual(quantidades.get(chave), totalQuantidade)));
        }
        return ranking;
    }

    // Consulta
    private List<PedidoConsiderado> consultar(FiltroDashboard filtro, Map<String, Produto> produtos) {
        LocalDateTime de = filtro.dataInicio().atStartOfDay();
        LocalDateTime ate = filtro.dataFim().plusDays(1).atStartOfDay();
        List<Pedido> pedidos = filtro.status() == null
                ? pedidoRepository.buscarPorPeriodo(de, ate)
                : pedidoRepository.buscarPorPeriodoEStatus(de, ate, PedidoValidacao.variantesStatus(filtro.status()));

        List<PedidoConsiderado> resultado = new ArrayList<>();
        for (Pedido pedido : pedidos) {
            List<ItemPedido> itens = pedido.getItens() == null ? List.of() : pedido.getItens().stream()
                    .filter(i -> filtro.categoria() == null
                            || filtro.categoria().equalsIgnoreCase(categoriaDoItem(i, produtos)))
                    .toList();
            if (filtro.categoria() != null && itens.isEmpty()) {
                continue;
            }

            double desconto = pedido.getDescontoPercentual() != null ? pedido.getDescontoPercentual() : 0.0;
            double valor;
            if (filtro.categoria() == null && pedido.getValorTotal() != null) {
                valor = pedido.getValorTotal();
            } else {
                double subtotal = itens.stream().mapToDouble(this::subtotal).sum();
                valor = subtotal - (subtotal * desconto / 100);
            }
            resultado.add(new PedidoConsiderado(pedido, normalizarStatus(pedido.getStatus()), itens, desconto, valor));
        }
        return resultado;
    }

    private List<PedidoConsiderado> validos(List<PedidoConsiderado> pedidos) {
        return pedidos.stream().filter(p -> !p.cancelado()).toList();
    }

    private Map<String, Produto> produtosPorId() {
        Map<String, Produto> produtos = new HashMap<>();
        produtoRepository.findAll().forEach(p -> produtos.put(p.getId(), p));
        return produtos;
    }

    private List<String> listarCategorias() {
        return produtoRepository.findAll().stream()
                .map(Produto::getTipo)
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private String categoriaDoItem(ItemPedido item, Map<String, Produto> produtos) {
        Produto produto = item.getProdutoId() != null ? produtos.get(item.getProdutoId()) : null;
        if (produto == null || produto.getTipo() == null || produto.getTipo().isBlank()) {
            return SEM_CATEGORIA;
        }
        return produto.getTipo().trim();
    }

    private String nomeProduto(ItemPedido item, Map<String, Produto> produtos) {
        if (item.getNomeProduto() != null && !item.getNomeProduto().isBlank()) {
            return item.getNomeProduto();
        }
        Produto produto = item.getProdutoId() != null ? produtos.get(item.getProdutoId()) : null;
        return produto != null && produto.getNome() != null ? produto.getNome() : "Produto removido";
    }

    private double subtotal(ItemPedido item) {
        if (item.getSubtotal() != null) {
            return item.getSubtotal();
        }
        return item.getPrecoUnitario() != null ? item.getPrecoUnitario() * item.getQuantidade() : 0.0;
    }

    private double valorComDesconto(ItemPedido item, PedidoConsiderado pedido) {
        double subtotal = subtotal(item);
        return subtotal - (subtotal * pedido.descontoPercentual() / 100);
    }

    private String normalizarStatus(String status) {
        if (status == null || status.isBlank()) {
            return StatusPedido.RECEBIDO;
        }
        return PedidoValidacao.lerStatus(status);
    }

    private String validarStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String lido = PedidoValidacao.lerStatus(status);
        if (!STATUS_PEDIDO.contains(lido)) {
            throw new IllegalArgumentException("Status inválido: " + status + ".");
        }
        return lido;
    }

    private String validarCategoria(String categoria) {
        if (categoria == null || categoria.isBlank()) {
            return null;
        }
        return listarCategorias().stream()
                .filter(c -> c.equalsIgnoreCase(categoria.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Categoria inválida: " + categoria + "."));
    }

    private int validarLimite(Integer limite, int padrao) {
        if (limite == null) {
            return padrao;
        }
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new IllegalArgumentException("O limite deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        return limite;
    }

    private String definirAgrupamento(FiltroDashboard filtro, String agrupamento) {
        if (agrupamento == null || agrupamento.isBlank()) {
            if (filtro.dias() <= 31) {
                return "DIA";
            }
            return filtro.dias() <= 120 ? "SEMANA" : "MES";
        }
        String tipo = agrupamento.trim().toUpperCase();
        if (!AGRUPAMENTOS.contains(tipo)) {
            throw new IllegalArgumentException("Agrupamento inválido: " + agrupamento + ". Use DIA, SEMANA ou MES.");
        }
        return tipo;
    }

    private LocalDate inicioDoGrupo(LocalDate data, String tipo) {
        return switch (tipo) {
            case "SEMANA" -> data.with(DayOfWeek.MONDAY);
            case "MES" -> data.withDayOfMonth(1);
            default -> data;
        };
    }

    private LocalDate proximoGrupo(LocalDate data, String tipo) {
        return switch (tipo) {
            case "SEMANA" -> data.plusWeeks(1);
            case "MES" -> data.plusMonths(1);
            default -> data.plusDays(1);
        };
    }

    private String rotulo(LocalDate data, String tipo) {
        return switch (tipo) {
            case "SEMANA" -> "Sem. " + data.format(ROTULO_DIA);
            case "MES" -> data.format(ROTULO_MES);
            default -> data.format(ROTULO_DIA);
        };
    }

    private double percentual(double parte, double total) {
        return total == 0 ? 0.0 : arredondar(parte * 100 / total);
    }

    private double arredondar(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
