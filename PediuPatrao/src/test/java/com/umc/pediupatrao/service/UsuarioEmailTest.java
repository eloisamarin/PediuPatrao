package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.Usuario;
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

/** E-mail no cadastro de usuários: opcional, formato válido, minúsculo e único. */
class UsuarioEmailTest {

    private UsuarioRepository repo;
    private UsuarioService service;

    @BeforeEach
    void preparar() {
        repo = mock(UsuarioRepository.class);
        service = new UsuarioService(repo, new BCryptPasswordEncoder(4), mock(AuditoriaService.class), mock(SessaoService.class));
        when(repo.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findByUsername(anyString())).thenReturn(Optional.empty());
        when(repo.findByEmail(anyString())).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "chefe", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private static UsuarioForm form(String username, String email) {
        UsuarioForm f = new UsuarioForm();
        f.setUsername(username);
        f.setPassword("Senha1234");
        f.setRole("ATENDENTE");
        f.setEmail(email);
        return f;
    }

    @Test
    void emailEGravadoEmMinusculoESemEspacos() {
        assertEquals("joao@pizzaria.com", service.criarUsuario(form("joao", "  Joao@Pizzaria.COM ")).getEmail());
    }

    @Test
    void emailEOpcional() {
        assertNull(service.criarUsuario(form("joao", "")).getEmail());
        assertNull(service.criarUsuario(form("maria", null)).getEmail());
    }

    @Test
    void emailInvalidoERecusado() {
        for (String invalido : new String[] { "joao", "joao@", "@pizzaria.com", "joao@pizzaria", "jo ao@x.com" }) {
            assertEquals("E-mail inválido.",
                    assertThrows(RegraNegocioException.class, () -> service.criarUsuario(form("joao", invalido))).getMessage());
        }
    }

    @Test
    void emailRepetidoERecusado_masOProprioUsuarioPodeMante_lo() {
        Usuario existente = new Usuario();
        existente.setId("u1");
        existente.setUsername("joao");
        existente.setRole("ATENDENTE");
        existente.setEmail("joao@pizzaria.com");
        when(repo.findByEmail("joao@pizzaria.com")).thenReturn(Optional.of(existente));
        when(repo.findById("u1")).thenReturn(Optional.of(existente));

        assertThrows(RegraNegocioException.class, () -> service.criarUsuario(form("maria", "joao@pizzaria.com")));

        UsuarioForm edicao = form("joao", "joao@pizzaria.com");
        edicao.setPassword(null);
        assertEquals("joao@pizzaria.com", service.atualizarUsuario("u1", edicao).getEmail());
    }
}
