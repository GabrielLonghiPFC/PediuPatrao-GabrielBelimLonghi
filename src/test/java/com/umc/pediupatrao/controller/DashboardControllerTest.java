package com.umc.pediupatrao.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.dto.FiltroDashboard;
import com.umc.pediupatrao.dto.IndicadoresDashboard;
import com.umc.pediupatrao.dto.ProdutoRanking;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 30);
    private static final FiltroDashboard FILTRO = new FiltroDashboard(INICIO, FIM, null, null);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @BeforeEach
    void configurar() {
        when(dashboardService.montarFiltro(any(), any(), any(), any())).thenReturn(FILTRO);
        when(dashboardService.indicadores(any())).thenReturn(new IndicadoresDashboard(
                INICIO, FIM, 10, 9, 1, 900.0, 100.0, 10.0));
    }

    @Test
    void semLoginRedirecionaParaOLogin() throws Exception {
        mockMvc.perform(get("/api/dashboard/indicadores"))
                .andExpect(status().is3xxRedirection());
        verify(dashboardService, never()).indicadores(any());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoAcessaODashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard/indicadores"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("Sem permissão."));
        verify(dashboardService, never()).indicadores(any());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteConsultaIndicadoresComFiltros() throws Exception {
        mockMvc.perform(get("/api/dashboard/indicadores")
                        .param("dataInicio", "2026-11-01")
                        .param("dataFim", "2026-11-30")
                        .param("status", "FINALIZADO")
                        .param("categoria", "Pizza"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataInicio").value("2026-11-01"))
                .andExpect(jsonPath("$.faturamento").value(900.0))
                .andExpect(jsonPath("$.ticketMedio").value(100.0))
                .andExpect(jsonPath("$.taxaCancelamento").value(10.0));
        verify(dashboardService).montarFiltro(INICIO, FIM, "FINALIZADO", "Pizza");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminConsultaRanking() throws Exception {
        when(dashboardService.produtosMaisVendidos(any(), eq(3))).thenReturn(List.of(
                new ProdutoRanking(1, "p1", "Pizza Calabresa", "Pizza", 12, 598.8, 45.0)));

        mockMvc.perform(get("/api/dashboard/produtos/mais-vendidos").param("limite", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].posicao").value(1))
                .andExpect(jsonPath("$[0].nome").value("Pizza Calabresa"));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void dataEmFormatoInvalidoRetorna400() throws Exception {
        mockMvc.perform(get("/api/dashboard/indicadores").param("dataInicio", "01/11/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").value(
                        "Parâmetro 'dataInicio' inválido: '01/11/2026'. Use o formato yyyy-MM-dd."))
                .andExpect(jsonPath("$.caminho").value("/api/dashboard/indicadores"));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void limiteNaoNumericoRetorna400() throws Exception {
        mockMvc.perform(get("/api/dashboard/pedidos/recentes").param("limite", "dez"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Parâmetro 'limite' inválido: 'dez'. Informe um número inteiro."));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void regraDeNegocioVioladaRetorna400ComMensagem() throws Exception {
        when(dashboardService.montarFiltro(eq(FIM), eq(INICIO), isNull(), isNull()))
                .thenThrow(new IllegalArgumentException("A data inicial não pode ser posterior à data final."));

        mockMvc.perform(get("/api/dashboard/indicadores")
                        .param("dataInicio", "2026-11-30")
                        .param("dataFim", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A data inicial não pode ser posterior à data final."));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void bancoIndisponivelRetorna503() throws Exception {
        when(dashboardService.pedidosPorStatus(any()))
                .thenThrow(new DataAccessResourceFailureException("Mongo fora do ar"));

        mockMvc.perform(get("/api/dashboard/pedidos/status"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.erro").value("Não foi possível consultar o banco de dados. Tente novamente."));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void escritaNoDashboardNaoEPermitida() throws Exception {
        mockMvc.perform(delete("/api/dashboard/indicadores").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
