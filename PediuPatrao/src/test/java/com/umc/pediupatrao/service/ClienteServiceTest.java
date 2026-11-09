package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClienteServiceTest {

    private ClienteRepository repo;
    private AuditoriaService auditoria;
    private ClienteService service;

    @BeforeEach
    void preparar() {
        repo = mock(ClienteRepository.class);
        auditoria = mock(AuditoriaService.class);
        service = new ClienteService(repo, auditoria);
        when(repo.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId("novo");
            }
            return c;
        });
    }

    private static Cliente cliente(String logradouro, String telefone, String bairro) {
        Cliente c = new Cliente();
        c.setNome("Maria");
        c.setLogradouro(logradouro);
        c.setTelefone(telefone);
        c.setBairro(bairro);
        c.setCidade("São Paulo");
        return c;
    }

    @SuppressWarnings("unchecked")
    private List<CampoAlterado> camposAuditados(String acao) {
        ArgumentCaptor<List<CampoAlterado>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoria).registrarAlteracao(eq(acao), eq("Cliente"), anyString(), captor.capture(), isNull(), isNull());
        return captor.getValue();
    }

    @Test
    void alteracaoDeEnderecoRegistraCampoValorAnteriorENovo() {
        Cliente antes = cliente("Rua das Flores", "(11) 90000-1111", "Centro");
        antes.setId("c1");
        when(repo.findById("c1")).thenReturn(Optional.of(antes));

        service.atualizar("c1", cliente("Av. Brasil", "(11) 90000-1111", "Centro"));

        List<CampoAlterado> campos = camposAuditados("ATUALIZAR_CLIENTE");
        assertEquals(1, campos.size()); // só o que mudou
        assertEquals("logradouro", campos.get(0).getCampo());
        assertEquals("Rua das Flores", campos.get(0).getValorAnterior());
        assertEquals("Av. Brasil", campos.get(0).getValorNovo());
    }

    @Test
    void alteracaoDeTelefoneEBairroRegistraAmbos() {
        Cliente antes = cliente("Rua A", "(11) 90000-1111", "Centro");
        when(repo.findById("c1")).thenReturn(Optional.of(antes));

        service.atualizar("c1", cliente("Rua A", "(11) 92222-3333", "Vila Nova"));

        Map<String, String[]> campos = camposAuditados("ATUALIZAR_CLIENTE").stream()
                .collect(Collectors.toMap(CampoAlterado::getCampo, c -> new String[] { c.getValorAnterior(), c.getValorNovo() }));
        assertEquals(2, campos.size());
        assertEquals("(11) 90000-1111", campos.get("telefone")[0]); // mascarado depois, pelo AuditoriaService
        assertEquals("(11) 92222-3333", campos.get("telefone")[1]);
        assertEquals("Centro", campos.get("bairro")[0]);
        assertEquals("Vila Nova", campos.get("bairro")[1]);
    }

    @Test
    void semMudancasNaoGeraRegistro() {
        when(repo.findById("c1")).thenReturn(Optional.of(cliente("Rua A", "1", "Centro")));

        service.atualizar("c1", cliente("Rua A", "1", "Centro"));

        verify(auditoria, never()).registrarAlteracao(anyString(), anyString(), anyString(), anyList(), any(), any());
    }

    @Test
    void inclusaoRegistraCamposPreenchidos() {
        service.cadastrar(cliente("Rua das Flores", "(11) 90000-1111", "Centro"));

        List<String> nomes = camposAuditados("CRIAR_CLIENTE").stream().map(CampoAlterado::getCampo).toList();
        assertEquals(List.of("nome", "telefone", "logradouro", "bairro", "cidade"), nomes);
    }
}
