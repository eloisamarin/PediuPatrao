package com.umc.pediupatrao.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.config.SegurancaBeansConfig;
import com.umc.pediupatrao.controller.api.DashboardApiController;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.TentativasLoginService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API do Dashboard com o Spring Security real: só ADMIN e GERENTE acessam, e filtros inválidos
 * voltam como 400 {"erro": "..."}.
 */
@WebMvcTest(controllers = DashboardApiController.class, properties = "pediupatrao.dashboard.api-key=chave-de-teste")
@Import({ SecurityConfig.class, SegurancaBeansConfig.class, LoginSucessoHandler.class, LoginFalhaHandler.class,
        AcessoNegadoHandler.class, ApiNaoAutenticadoEntryPoint.class, TentativasLoginService.class })
class DashboardRotasSegurancaTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void anonimoRecebe401() throws Exception {
        mvc.perform(get("/api/dashboard/resumo")).andExpect(status().isUnauthorized());
    }

    @Test
    void atendenteNaoAcessaODashboard() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").with(user("a").roles("ATENDENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/dashboard").with(user("a").roles("ATENDENTE"))).andExpect(status().isForbidden());
    }

    @Test
    void gerenteEAdminAcessam() throws Exception {
        when(dashboardService.pedidosPorStatus(any())).thenReturn(List.of());
        for (String perfil : new String[] { "GERENTE", "ADMIN" }) {
            mvc.perform(get("/api/dashboard/status").with(user("x").roles(perfil))).andExpect(status().isOk());
        }
    }

    // ===== Chave de API do Grafana (X-API-KEY) =====

    @Test
    void grafanaComChaveCorretaLeOResumo() throws Exception {
        when(dashboardService.pedidosPorStatus(any())).thenReturn(List.of());
        mvc.perform(get("/api/dashboard/status").header("X-API-KEY", "chave-de-teste")).andExpect(status().isOk());
    }

    @Test
    void chaveErradaOuAusenteRecebe401() throws Exception {
        mvc.perform(get("/api/dashboard/status").header("X-API-KEY", "errada")).andExpect(status().isUnauthorized());
    }

    @Test
    void chaveNaoAbreATelaNemOutrasApis() throws Exception {
        // A tela /dashboard e as demais APIs ignoram o cabeçalho (sem login → login/401)
        mvc.perform(get("/dashboard").header("X-API-KEY", "chave-de-teste")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/usuarios").header("X-API-KEY", "chave-de-teste")).andExpect(status().isUnauthorized());
    }

    @Test
    void filtroInvalidoRetorna400ComMensagem() throws Exception {
        when(dashboardService.resumo(any())).thenThrow(new RegraNegocioException("A data inicial não pode ser posterior à data final."));
        mvc.perform(get("/api/dashboard/resumo").param("inicio", "2026-11-07").param("fim", "2026-11-01")
                .with(user("g").roles("GERENTE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A data inicial não pode ser posterior à data final."));

        // Data em formato errado também vira 400 (tratado pelo ApiExceptionHandler)
        mvc.perform(get("/api/dashboard/resumo").param("inicio", "07/11/2026").with(user("g").roles("GERENTE")))
                .andExpect(status().isBadRequest());
    }
}
