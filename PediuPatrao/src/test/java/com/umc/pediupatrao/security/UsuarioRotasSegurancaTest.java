package com.umc.pediupatrao.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.config.SegurancaBeansConfig;
import com.umc.pediupatrao.controller.api.UsuarioApiController;
import com.umc.pediupatrao.controller.web.UsuarioWebController;
import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.TentativasLoginService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testa as rotas de cadastro e gestão de usuários com o Spring Security real (SecurityConfig):
 * perfis, CSRF, respostas da API e tratamento de erros. Os serviços são simulados.
 */
@WebMvcTest(controllers = { UsuarioWebController.class, UsuarioApiController.class })
@Import({ SecurityConfig.class, SegurancaBeansConfig.class, LoginSucessoHandler.class, LoginFalhaHandler.class,
        AcessoNegadoHandler.class, ApiNaoAutenticadoEntryPoint.class, TentativasLoginService.class })
class UsuarioRotasSegurancaTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    // ===== Autorização por perfil =====

    @Test
    void anonimoNaTelaVaiParaOLogin() throws Exception {
        mvc.perform(get("/usuarios")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void anonimoNaApiRecebe401EmJson() throws Exception {
        mvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void somenteAdminAcessaTelaEApiDeUsuarios() throws Exception {
        for (String perfil : new String[] { "GERENTE", "ATENDENTE" }) {
            mvc.perform(get("/usuarios").with(user("x").roles(perfil))).andExpect(status().isForbidden());
            mvc.perform(get("/api/usuarios").with(user("x").roles(perfil))).andExpect(status().isForbidden());
            mvc.perform(post("/usuarios/salvar").with(user("x").roles(perfil)).with(csrf())
                    .param("username", "novo").param("password", "Senha123").param("role", "ADMIN"))
                    .andExpect(status().isForbidden());
        }
        verify(usuarioService, never()).criarUsuario(any());
        when(usuarioService.listarTodos()).thenReturn(List.of());
        mvc.perform(get("/api/usuarios").with(user("chefe").roles("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void acessoNegadoEhAuditado() throws Exception {
        mvc.perform(get("/usuarios").with(user("atendente01").roles("ATENDENTE"))).andExpect(status().isForbidden());
        verify(auditoriaService).registrar(any(org.springframework.security.core.Authentication.class),
                eq("ACESSO_NEGADO"), any(), any(), anyString());
    }

    // ===== CSRF =====

    @Test
    void criarUsuarioSemTokenCsrfEhBloqueadoMesmoParaAdmin() throws Exception {
        mvc.perform(post("/usuarios/salvar").with(user("chefe").roles("ADMIN"))
                .param("username", "novo").param("password", "Senha123").param("role", "ATENDENTE"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/usuarios/salvar").with(user("chefe").roles("ADMIN")).with(csrf().useInvalidToken())
                .param("username", "novo").param("password", "Senha123").param("role", "ATENDENTE"))
                .andExpect(status().isForbidden());
        verify(usuarioService, never()).criarUsuario(any());
        verify(auditoriaService, org.mockito.Mockito.atLeastOnce()).registrar(
                any(org.springframework.security.core.Authentication.class), eq("CSRF_INVALIDO"), any(), any(), anyString());
    }

    @Test
    void alterarEExcluirUsuarioExigemCsrf() throws Exception {
        mvc.perform(post("/usuarios/editar/1").with(user("chefe").roles("ADMIN")).param("role", "ADMIN"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/usuarios/deletar/1").with(user("chefe").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/usuarios/1").with(user("chefe").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.containsString("CSRF")));
        mvc.perform(delete("/api/usuarios/1").with(user("chefe").roles("ADMIN"))).andExpect(status().isForbidden());
        verify(usuarioService, never()).atualizarUsuario(anyString(), any());
        verify(usuarioService, never()).deletarUsuario(anyString());
    }

    @Test
    void sessaoExpiradaComFormularioAbertoVaiParaLogin() throws Exception {
        mvc.perform(post("/usuarios/salvar").param("username", "novo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expirada"));
    }

    @Test
    void logoutSoAceitaPostComCsrf() throws Exception {
        mvc.perform(get("/logout").with(user("chefe").roles("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(post("/logout").with(user("chefe").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(user("chefe").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));
    }

    // ===== Fluxo normal e tratamento de erros =====

    @Test
    void adminCriaUsuarioComCsrf() throws Exception {
        when(usuarioService.criarUsuario(any(UsuarioForm.class))).thenReturn(new Usuario());
        mvc.perform(post("/usuarios/salvar").with(user("chefe").roles("ADMIN")).with(csrf())
                .param("username", "novo").param("password", "Senha123").param("role", "ATENDENTE"))
                .andExpect(redirectedUrl("/usuarios"))
                .andExpect(flash().attribute("sucesso", "Usuário criado com sucesso!"));
    }

    @Test
    void regraDeNegocioVoltaComoMensagemNaTela() throws Exception {
        when(usuarioService.criarUsuario(any(UsuarioForm.class))).thenThrow(new RegraNegocioException("Perfil inválido."));
        mvc.perform(post("/usuarios/salvar").with(user("chefe").roles("ADMIN")).with(csrf())
                .param("username", "novo").param("password", "Senha123").param("role", "SUPERADMIN"))
                .andExpect(redirectedUrl("/usuarios"))
                .andExpect(flash().attribute("erro", "Perfil inválido."));
    }

    @Test
    void apiDevolve400ParaRegraE404ParaInexistente() throws Exception {
        when(usuarioService.criarUsuario(any(UsuarioForm.class))).thenThrow(new RegraNegocioException("Nome de usuário inválido."));
        mvc.perform(post("/api/usuarios").with(user("chefe").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"<b>\",\"password\":\"x\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Nome de usuário inválido."));

        when(usuarioService.buscarPorId("nao-existe")).thenThrow(new RecursoNaoEncontradoException("Usuário não encontrado."));
        mvc.perform(get("/api/usuarios/nao-existe").with(user("chefe").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Usuário não encontrado."));

        mvc.perform(post("/api/usuarios").with(user("chefe").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{json quebrado"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void apiNuncaDevolveHashDaSenha() throws Exception {
        Usuario u = new Usuario();
        u.setId("1");
        u.setUsername("maria");
        u.setRole("ATENDENTE");
        u.setPassword("$2a$10$hashsecreto");
        when(usuarioService.listarTodos()).thenReturn(List.of(u));
        mvc.perform(get("/api/usuarios").with(user("chefe").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("maria"))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hashsecreto"))));
    }
}
