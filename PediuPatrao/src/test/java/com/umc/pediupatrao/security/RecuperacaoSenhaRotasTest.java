package com.umc.pediupatrao.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.config.SegurancaBeansConfig;
import com.umc.pediupatrao.controller.web.RecuperacaoSenhaController;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.RecuperacaoSenhaService;
import com.umc.pediupatrao.service.TentativasLoginService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Telas do "Esqueci a senha" com o Spring Security real: são públicas, os POST exigem CSRF,
 * a resposta não revela se a conta existe, e o login sugere redefinir a senha na 3ª falha.
 */
@WebMvcTest(controllers = RecuperacaoSenhaController.class)
@Import({ SecurityConfig.class, SegurancaBeansConfig.class, LoginSucessoHandler.class, LoginFalhaHandler.class,
        AcessoNegadoHandler.class, ApiNaoAutenticadoEntryPoint.class, TentativasLoginService.class })
class RecuperacaoSenhaRotasTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private LoginFalhaHandler loginFalha;
    @Autowired
    private TentativasLoginService tentativas;

    @MockitoBean
    private RecuperacaoSenhaService recuperacaoSenha;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void telasSaoPublicas() throws Exception {
        mvc.perform(get("/esqueci-senha")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Usuário ou e-mail")));
        mvc.perform(get("/redefinir-senha").param("token", "x")).andExpect(status().isOk());
    }

    @Test
    void pedirLink_sempreMesmaRespostaExistaOuNaoAConta() throws Exception {
        mvc.perform(post("/esqueci-senha").with(csrf()).param("identificador", "joao"))
                .andExpect(redirectedUrl("/esqueci-senha?enviado"));
        mvc.perform(post("/esqueci-senha").with(csrf()).param("identificador", "nao-existe"))
                .andExpect(redirectedUrl("/esqueci-senha?enviado"));
        verify(recuperacaoSenha).solicitar("joao");
    }

    @Test
    void postSemCsrf_eBloqueado() throws Exception {
        // Visitante sem sessão e sem token CSRF: o sistema barra e manda para o login (comportamento padrão do projeto)
        mvc.perform(post("/esqueci-senha").param("identificador", "joao")).andExpect(redirectedUrl("/login?expirada"));
        mvc.perform(post("/redefinir-senha").param("token", "t").param("senha", "NovaSenha1").param("confirmacao", "NovaSenha1"))
                .andExpect(redirectedUrl("/login?expirada"));
        verify(recuperacaoSenha, never()).solicitar(anyString());
        verify(recuperacaoSenha, never()).redefinir(anyString(), anyString(), anyString());
    }

    @Test
    void linkInvalido_mostraAvisoEPedeNovoLink() throws Exception {
        when(recuperacaoSenha.tokenValido("vencido")).thenReturn(false);
        mvc.perform(get("/redefinir-senha").param("token", "vencido"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Pedir um novo link")));
    }

    @Test
    void redefinirComSucesso_voltaParaOLoginComMensagem() throws Exception {
        mvc.perform(post("/redefinir-senha").with(csrf())
                        .param("token", "t").param("senha", "NovaSenha1").param("confirmacao", "NovaSenha1"))
                .andExpect(redirectedUrl("/login?senhaRedefinida"));
        verify(recuperacaoSenha).redefinir("t", "NovaSenha1", "NovaSenha1");
    }

    @Test
    void redefinirComErro_mostraMensagemNaMesmaTela() throws Exception {
        when(recuperacaoSenha.tokenValido("t")).thenReturn(true);
        doThrow(new RegraNegocioException("A confirmação não confere com a nova senha."))
                .when(recuperacaoSenha).redefinir(eq("t"), any(), any());
        mvc.perform(post("/redefinir-senha").with(csrf())
                        .param("token", "t").param("senha", "NovaSenha1").param("confirmacao", "Outra1234"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("A confirmação não confere")));
    }

    // ===== Sugestão de redefinir a senha depois de 3 erros (e bloqueio na 5ª) =====

    @Test
    void login_terceiraSenhaErradaSugereRedefinirEQuintaBloqueia() throws Exception {
        String[] destinos = new String[5];
        for (int i = 0; i < 5; i++) {
            HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getParameter("username")).thenReturn("maria");
            when(req.getContextPath()).thenReturn("");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            loginFalha.onAuthenticationFailure(req, resp, new BadCredentialsException("x"));
            destinos[i] = resp.getRedirectedUrl();
        }
        org.junit.jupiter.api.Assertions.assertArrayEquals(new String[] {
                "/login?error", "/login?error", "/login?error&redefinir", "/login?error&redefinir", "/login?bloqueado" },
                destinos);
        tentativas.limpar("maria");
    }
}
