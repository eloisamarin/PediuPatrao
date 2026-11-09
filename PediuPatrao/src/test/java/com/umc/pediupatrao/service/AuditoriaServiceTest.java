package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditoriaServiceTest {

    private MongoTemplate mongoTemplate;
    private AuditoriaService service;

    @BeforeEach
    void preparar() {
        mongoTemplate = mock(MongoTemplate.class);
        service = new AuditoriaService(mock(AuditoriaRepository.class), mongoTemplate);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "atendente01", null, List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE"))));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private Auditoria gravado() {
        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(mongoTemplate).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    void registraUsuarioPerfilDataOperacaoEntidadeCamposEJustificativa() {
        service.registrarAlteracao("ATUALIZAR_CLIENTE", "Cliente", "c1",
                List.of(new CampoAlterado("logradouro", "Rua das Flores", "Av. Brasil")), "Cliente mudou-se", null);

        Auditoria a = gravado();
        assertEquals("atendente01", a.getUsuario());
        assertEquals("ATENDENTE", a.getPerfil());
        assertNotNull(a.getDataHora());
        assertEquals("ATUALIZAR_CLIENTE", a.getAcao());
        assertEquals("ALTERAÇÃO DE CLIENTE", a.getOperacao());
        assertEquals("Cliente", a.getEntidade());
        assertEquals("c1", a.getEntidadeId());
        assertEquals(1, a.getCampos().size());
        assertEquals("logradouro", a.getCampos().get(0).getCampo());
        assertEquals("Rua das Flores", a.getCampos().get(0).getValorAnterior());
        assertEquals("Av. Brasil", a.getCampos().get(0).getValorNovo());
        assertEquals("Cliente mudou-se", a.getJustificativa());
    }

    @Test
    void usaInsertENuncaSave() {
        service.registrar("LOGIN", "Usuario", null, "Login realizado");

        verify(mongoTemplate).insert(any(Auditoria.class));
        verify(mongoTemplate, never()).save(any());
    }

    @Test
    void nuncaGravaSenhasTokensOuCredenciais() {
        service.registrarAlteracao("ALTERAR_SENHA", "Usuario", "u1", List.of(
                new CampoAlterado("senha", "teste123", "novaSenha!"),
                new CampoAlterado("password", "x", "y"),
                new CampoAlterado("tokenAcesso", "abc", "def"),
                new CampoAlterado("_csrf", "1", "2")), null, null);

        for (CampoAlterado c : gravado().getCampos()) {
            assertEquals(AuditoriaService.PROTEGIDO, c.getValorAnterior(), c.getCampo());
            assertEquals(AuditoriaService.PROTEGIDO, c.getValorNovo(), c.getCampo());
        }
    }

    @Test
    void mascaraTelefoneMantendoOs4UltimosDigitos() {
        service.registrarAlteracao("ATUALIZAR_CLIENTE", "Cliente", "c1",
                List.of(new CampoAlterado("telefone", "(11) 98765-4321", "(11) 91234-5678")), null, null);

        CampoAlterado c = gravado().getCampos().get(0);
        assertEquals("(**) *****-4321", c.getValorAnterior());
        assertEquals("(**) *****-5678", c.getValorNovo());
        assertFalse(c.getValorAnterior().contains("98765"));
    }

    @Test
    void mascaraTelefoneNuloOuCurto() {
        assertNull(AuditoriaService.mascararTelefone(null));
        assertEquals("1234", AuditoriaService.mascararTelefone("1234"));
    }

    @Test
    void camposDoRegistroNaoPodemSerAlteradosDepoisDeCriado() {
        service.registrarAlteracao("ALTERAR_STATUS", "Pedido", "p1",
                List.of(new CampoAlterado("status", "RECEBIDO", "EM_PREPARACAO")), null, null);
        Auditoria a = gravado();

        // Sem setters públicos e lista de campos imutável
        boolean temSetterPublico = Arrays.stream(Auditoria.class.getMethods()).anyMatch(m -> m.getName().startsWith("set"));
        assertFalse(temSetterPublico, "Auditoria não deve ter setters públicos");
        boolean campoTemSetter = Arrays.stream(CampoAlterado.class.getMethods()).anyMatch(m -> m.getName().startsWith("set"));
        assertFalse(campoTemSetter, "CampoAlterado não deve ter setters públicos");
        try {
            a.getCampos().clear();
            throw new AssertionError("A lista de campos deveria ser imutável");
        } catch (UnsupportedOperationException esperado) {
            assertEquals(1, a.getCampos().size());
        }
    }

    @Test
    void repositorioNaoOfereceSalvarNemExcluir() {
        List<String> metodos = Arrays.stream(AuditoriaRepository.class.getMethods()).map(Method::getName).toList();
        assertTrue(metodos.stream().noneMatch(n -> n.startsWith("save") || n.startsWith("delete")
                || n.startsWith("insert") || n.startsWith("update") || n.startsWith("remove")),
                "AuditoriaRepository expõe: " + metodos);
    }
}
