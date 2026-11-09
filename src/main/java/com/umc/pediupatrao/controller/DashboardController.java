package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.IndicadoresDashboard;
import com.umc.pediupatrao.dto.OpcoesFiltroDashboard;
import com.umc.pediupatrao.dto.PedidoRecente;
import com.umc.pediupatrao.dto.ProdutoRanking;
import com.umc.pediupatrao.dto.StatusPedidoResumo;
import com.umc.pediupatrao.dto.VendaCategoria;
import com.umc.pediupatrao.dto.VendasPorPeriodo;
import com.umc.pediupatrao.service.DashboardService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/filtros")
    public OpcoesFiltroDashboard filtros() {
        return dashboardService.opcoesFiltro();
    }

    @GetMapping("/indicadores")
    public IndicadoresDashboard indicadores(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria) {
        return dashboardService.indicadores(dashboardService.montarFiltro(dataInicio, dataFim, status, categoria));
    }

    @GetMapping("/vendas")
    public VendasPorPeriodo vendas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String agrupamento) {
        return dashboardService.vendasPorPeriodo(
                dashboardService.montarFiltro(dataInicio, dataFim, status, categoria), agrupamento);
    }

    @GetMapping("/vendas/categorias")
    public List<VendaCategoria> vendasPorCategoria(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria) {
        return dashboardService.vendasPorCategoria(dashboardService.montarFiltro(dataInicio, dataFim, status, categoria));
    }

    @GetMapping("/pedidos/status")
    public List<StatusPedidoResumo> pedidosPorStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria) {
        return dashboardService.pedidosPorStatus(dashboardService.montarFiltro(dataInicio, dataFim, status, categoria));
    }

    @GetMapping("/pedidos/recentes")
    public List<PedidoRecente> pedidosRecentes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.pedidosRecentes(
                dashboardService.montarFiltro(dataInicio, dataFim, status, categoria), limite);
    }

    @GetMapping("/produtos/mais-vendidos")
    public List<ProdutoRanking> produtosMaisVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.produtosMaisVendidos(
                dashboardService.montarFiltro(dataInicio, dataFim, status, categoria), limite);
    }
}
