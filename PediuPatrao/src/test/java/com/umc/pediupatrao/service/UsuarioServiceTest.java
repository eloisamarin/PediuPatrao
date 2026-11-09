package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UsuarioServiceTest {

    private UsuarioRepository repo;
    private SessaoService sessoes;
    private UsuarioService service;

    @BeforeEach
    void preparar() {
        repo = mock(UsuarioRepository.class);
        sessoes = mock(SessaoService.class);
        service = new UsuarioService(repo, new BCryptPasswordEncoder(4), mock(AuditoriaService.class), sessoes);
        when(repo.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findByUsername(anyString())).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "chefe", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private static UsuarioForm form(String username, String senha, String perfil) {
        UsuarioForm f = new UsuarioForm();
        f.setUsername(username);
        f.setPassword(senha);
        f.setRole(perfil);
        return f;
    }

    private static Usuario usuario(String id, String username, String role) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setRole(role);
        u.setPassword("hash");
        return u;
    }

    @Test
    void criaUsuarioComSenhaCriptografada() {
        Usuario u = service.criarUsuario(form("joao.silva", "Senha123", "ATENDENTE"));
        assertEquals("joao.silva", u.getUsername());
        assertEquals("ATENDENTE", u.getRole());
        assertTrue(u.getPassword().startsWith("$2"), "senha deve ser hash BCrypt");
    }

    @Test
    void recusaUsernameInvalido() {
        for (String nome : new String[] { null, "", "ab", "<b>x</b>", "joão", "nome com espaço", "a".repeat(31), "x;drop" }) {
            assertThrows(RegraNegocioException.class, () -> service.criarUsuario(form(nome, "Senha123", "ATENDENTE")), "aceitou " + nome);
        }
    }

    @Test
    void recusaSenhaFraca() {
        for (String senha : new String[] { null, "", "abc1234", "somenteletras", "12345678", "a1" + "x".repeat(71) }) {
            assertThrows(RegraNegocioException.class, () -> service.criarUsuario(form("maria", senha, "ATENDENTE")),
                    "aceitou senha de " + (senha == null ? 0 : senha.length()) + " caracteres");
        }
    }

    @Test
    void recusaPerfilInexistente() {
        for (String perfil : new String[] { null, "", "SUPERADMIN", "USER", "root" }) {
            assertThrows(RegraNegocioException.class, () -> service.criarUsuario(form("maria", "Senha123", perfil)));
        }
    }

    @Test
    void recusaUsernameDuplicado() {
        when(repo.findByUsername("maria")).thenReturn(Optional.of(usuario("1", "maria", "ATENDENTE")));
        assertThrows(RegraNegocioException.class, () -> service.criarUsuario(form("maria", "Senha123", "ATENDENTE")));
    }

    @Test
    void naoAlteraOProprioPerfil() {
        when(repo.findById("1")).thenReturn(Optional.of(usuario("1", "chefe", "ADMIN")));
        assertThrows(RegraNegocioException.class, () -> service.atualizarUsuario("1", form(null, null, "ATENDENTE")));
    }

    @Test
    void naoRemoveOUltimoAdmin() {
        when(repo.findById("2")).thenReturn(Optional.of(usuario("2", "outro", "ADMIN")));
        when(repo.countByRole("ADMIN")).thenReturn(1L);
        assertThrows(RegraNegocioException.class, () -> service.atualizarUsuario("2", form(null, null, "GERENTE")));
        assertThrows(RegraNegocioException.class, () -> service.deletarUsuario("2"));
    }

    @Test
    void naoExcluiOProprioUsuario() {
        when(repo.findById("1")).thenReturn(Optional.of(usuario("1", "chefe", "ADMIN")));
        assertThrows(RegraNegocioException.class, () -> service.deletarUsuario("1"));
    }

    @Test
    void edicaoIgnoraUsernameEnviado() {
        Usuario existente = usuario("3", "maria", "ATENDENTE");
        when(repo.findById("3")).thenReturn(Optional.of(existente));

        service.atualizarUsuario("3", form("hacker", null, "GERENTE"));

        assertEquals("maria", existente.getUsername());
        assertEquals("GERENTE", existente.getRole());
    }

    @Test
    void mudarPerfilOuSenhaEncerraSessoesDoUsuario() {
        when(repo.findById("3")).thenReturn(Optional.of(usuario("3", "maria", "ATENDENTE")));
        service.atualizarUsuario("3", form(null, null, "GERENTE"));
        verify(sessoes).encerrarSessoesDe("maria");

        when(repo.findById("4")).thenReturn(Optional.of(usuario("4", "jose", "ATENDENTE")));
        service.atualizarUsuario("4", form(null, "NovaSenha9", "ATENDENTE"));
        verify(sessoes).encerrarSessoesDe("jose");
    }

    @Test
    void semMudancaNaoEncerraSessao() {
        when(repo.findById("3")).thenReturn(Optional.of(usuario("3", "maria", "ATENDENTE")));
        service.atualizarUsuario("3", form(null, "", "ATENDENTE"));
        verify(sessoes, never()).encerrarSessoesDe(anyString());
    }

    @Test
    void validacaoFalhaNaoGravaNadaPelaMetade() {
        Usuario existente = usuario("3", "maria", "ATENDENTE");
        when(repo.findById("3")).thenReturn(Optional.of(existente));

        // perfil válido + senha inválida: nada pode ser alterado
        assertThrows(RegraNegocioException.class, () -> service.atualizarUsuario("3", form(null, "curta1", "GERENTE")));
        assertEquals("ATENDENTE", existente.getRole());
        verify(repo, never()).save(any());
    }

    @Test
    void usuarioInexistenteEhRecursoNaoEncontrado() {
        when(repo.findById("x")).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscarPorId("x"));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.deletarUsuario("x"));
    }

    @Test
    void formularioNuncaExpoeSenhaNoToString() {
        assertTrue(!form("maria", "Segredo123", "ATENDENTE").toString().contains("Segredo123"));
    }
}
