package com.umc.pediupatrao.service;

import com.umc.pediupatrao.exception.RegraNegocioException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.HistoricoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Regras de cálculo e registro do pedido (sem banco e sem servidor: repositórios simulados com Mockito).
 * As permissões por perfil (@PreAuthorize) são verificadas no teste de ponta a ponta com o sistema rodando.
 */
class PedidoServiceTest {

    private PedidoRepository pedidoRepository;
    private ProdutoRepository produtoRepository;
    private PedidoService service;

    @BeforeEach
    void preparar() {
        pedidoRepository = mock(PedidoRepository.class);
        ClienteRepository clienteRepository = mock(ClienteRepository.class);
        produtoRepository = mock(ProdutoRepository.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        service = new PedidoService(pedidoRepository, clienteRepository, produtoRepository, auditoria);

        when(clienteRepository.existsById("c1")).thenReturn(true);
        when(produtoRepository.findById("pizza")).thenReturn(Optional.of(produto("pizza", "Pizza Grande", 50.0, true)));
        when(produtoRepository.findById("refri")).thenReturn(Optional.of(produto("refri", "Refrigerante", 8.0, true)));
        when(produtoRepository.findById("inativo")).thenReturn(Optional.of(produto("inativo", "Antigo", 10.0, false)));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        logarComo("atendente1", "ROLE_ATENDENTE");
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registraItensComPrecoDoCadastroResponsavelEDataHora() {
        LocalDateTime antes = LocalDateTime.now();

        Pedido pedido = service.criarPedido(requisicao(item("pizza", 2, "Calabresa"), item("refri", 3, null)));

        assertEquals(2, pedido.getItens().size());
        ItemPedido pizza = pedido.getItens().get(0);
        assertEquals("Pizza Grande", pizza.getNomeProduto());
        assertEquals(50.0, pizza.getPrecoUnitario());
        assertEquals(2, pizza.getQuantidade());
        assertEquals(100.0, pizza.getSubtotal());
        assertEquals("Calabresa", pizza.getSabor());

        assertEquals(124.0, pedido.getValorBruto());
        assertEquals(0.0, pedido.getDescontoPercentual());
        assertEquals(124.0, pedido.getValorFinal());
        assertEquals("RECEBIDO", pedido.getStatus());
        assertEquals("atendente1", pedido.getCriadoPor());
        assertNotNull(pedido.getDataCriacao());
        assertTrue(!pedido.getDataCriacao().isBefore(antes));
    }

    @Test
    void ignoraLinhasSemProduto() {
        Pedido pedido = service.criarPedido(requisicao(item("pizza", 1, null), item("", 5, null), item(null, 1, null)));

        assertEquals(1, pedido.getItens().size());
        assertEquals(50.0, pedido.getValorFinal());
    }

    @Test
    void recusaPedidoSemItens() {
        assertThrows(RegraNegocioException.class, () -> service.criarPedido(requisicao()));
    }

    @Test
    void recusaProdutoInativoOuInexistente() {
        when(produtoRepository.findById(anyString())).thenReturn(Optional.empty());
        when(produtoRepository.findById("inativo")).thenReturn(Optional.of(produto("inativo", "Antigo", 10.0, false)));

        assertThrows(RegraNegocioException.class, () -> service.criarPedido(requisicao(item("inativo", 1, null))));
        assertThrows(RegraNegocioException.class, () -> service.criarPedido(requisicao(item("naoexiste", 1, null))));
    }

    @Test
    void recusaQuantidadeForaDoLimite() {
        assertThrows(RegraNegocioException.class, () -> service.criarPedido(requisicao(item("pizza", 0, null))));
        assertThrows(RegraNegocioException.class, () -> service.criarPedido(requisicao(item("pizza", 101, null))));
    }

    @Test
    void solicitacaoDeDescontoNaCriacaoNaoAlteraOValor() {
        Pedido pedido = service.criarPedido(comDesconto(requisicao(item("pizza", 2, null), item("refri", 3, null)), "Cliente fiel"));

        assertTrue(pedido.isDescontoSolicitado());
        assertEquals("Cliente fiel", pedido.getMotivoDesconto());
        assertEquals("atendente1", pedido.getDescontoSolicitadoPor());
        assertNotNull(pedido.getDataSolicitacaoDesconto());
        assertEquals(0.0, pedido.getDescontoPercentual());
        assertEquals(124.0, pedido.getValorFinal()); // valor cheio até o gerente aprovar
    }

    @Test
    void gerenteAplicaDescontoSolicitadoERecalculaTotal() {
        Pedido pedido = service.criarPedido(comDesconto(requisicao(item("pizza", 2, null), item("refri", 3, null)), null)); // 124,00
        pedido.setId("p1");
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(pedido));
        logarComo("gerente1", "ROLE_GERENTE");

        service.aplicarDesconto("p1", 20.0);

        assertEquals(124.0, pedido.getValorBruto());
        assertEquals(24.8, pedido.getValorDesconto());
        assertEquals(99.2, pedido.getValorFinal());
        assertEquals("gerente1", pedido.getDescontoAplicadoPor());
        assertNotNull(pedido.getDataDesconto());
        assertFalse(pedido.isDescontoSolicitado()); // solicitação atendida
        // O gerente pode substituir o desconto: o total é recalculado sobre o valor bruto, não acumula
        service.aplicarDesconto("p1", 10.0);
        assertEquals(12.4, pedido.getValorDesconto());
        assertEquals(111.6, pedido.getValorFinal());
    }

    @Test
    void descontoLimitadoA20PorCento() {
        for (double pct : new double[] {5, 10, 12.5, 15, 20}) {
            Pedido pedido = service.criarPedido(comDesconto(requisicao(item("pizza", 2, null)), null)); // 100,00
            pedido.setId("p" + pct);
            when(pedidoRepository.findById("p" + pct)).thenReturn(Optional.of(pedido));
            service.aplicarDesconto("p" + pct, pct);
            assertEquals(100.0 - pct, pedido.getValorFinal());
        }

        Pedido pedido = service.criarPedido(comDesconto(requisicao(item("pizza", 1, null)), null));
        pedido.setId("px");
        when(pedidoRepository.findById("px")).thenReturn(Optional.of(pedido));
        for (double invalido : new double[] {0, -10, 20.01, 21, 50, 100, 150, Double.NaN}) {
            assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("px", invalido), "aceitou " + invalido);
        }
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("px", null));
        assertEquals(50.0, pedido.getValorFinal());
        assertTrue(pedido.isDescontoSolicitado()); // continua pendente
    }

    @Test
    void gerenteAplicaDescontoDiretoSemSolicitacao() {
        Pedido pedido = service.criarPedido(requisicao(item("pizza", 1, null))); // 50,00, sem solicitação
        pedido.setId("p1");
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(pedido));
        logarComo("gerente1", "ROLE_GERENTE");

        service.aplicarDesconto("p1", 7.5, "Cliente aguardou muito"); // valor digitado (não é atalho)

        assertEquals(7.5, pedido.getDescontoPercentual());
        assertEquals(3.75, pedido.getValorDesconto());
        assertEquals(46.25, pedido.getValorFinal());
        assertEquals("gerente1", pedido.getDescontoAplicadoPor());
    }

    @Test
    void descontoDiretoAcimaDe20NaoAlteraOPedido() {
        Pedido pedido = service.criarPedido(requisicao(item("pizza", 2, null))); // 100,00
        pedido.setId("p1");
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(pedido));

        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", 20.01, "tentativa"));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", 25.0, "tentativa"));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", Double.POSITIVE_INFINITY, null));
        assertEquals(100.0, pedido.getValorFinal());
        assertEquals(0.0, pedido.getDescontoPercentual());
    }

    @Test
    void rejeitarDescontoMantemValor() {
        Pedido pedido = service.criarPedido(comDesconto(requisicao(item("pizza", 1, null)), null));
        pedido.setId("p1");
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(pedido));

        service.rejeitarDesconto("p1");

        assertFalse(pedido.isDescontoSolicitado());
        assertEquals(50.0, pedido.getValorFinal());
        // Já rejeitada: não há mais solicitação pendente para rejeitar
        assertThrows(RegraNegocioException.class, () -> service.rejeitarDesconto("p1"));
    }

    // ===== Fluxo de status =====

    @Test
    void entradaRegistraRecebidoComDataHoraEResponsavelNoHistorico() {
        Pedido pedido = service.criarPedido(requisicao(item("pizza", 1, null)));

        assertEquals("RECEBIDO", pedido.getStatus());
        assertEquals(1, pedido.getHistorico().size());
        HistoricoStatus entrada = pedido.getHistorico().get(0);
        assertNull(entrada.getStatusAnterior());
        assertEquals("RECEBIDO", entrada.getStatusNovo());
        assertEquals("atendente1", entrada.getUsuario());
        assertNotNull(entrada.getDataHora());
    }

    @Test
    void fluxoCompletoComEntregaRegistraSaidaEFinalizacao() {
        Pedido pedido = pedidoSalvo("p1", requisicao(item("pizza", 1, null)));
        logarComo("gerente1", "ROLE_GERENTE");

        service.atualizarStatus("p1", "EM_PREPARACAO");
        service.atualizarStatus("p1", "PRONTO");
        assertNull(pedido.getDataSaida());
        service.atualizarStatus("p1", "SAIU_PARA_ENTREGA");
        assertNotNull(pedido.getDataSaida());
        assertEquals("gerente1", pedido.getResponsavelSaida());
        assertEquals("SAIU_PARA_ENTREGA", pedido.getTipoSaida());
        service.atualizarStatus("p1", "FINALIZADO");

        assertEquals("FINALIZADO", pedido.getStatus());
        assertNotNull(pedido.getDataFinalizacao());
        assertEquals("gerente1", pedido.getFinalizadoPor());
        assertEquals(List.of("RECEBIDO", "EM_PREPARACAO", "PRONTO", "SAIU_PARA_ENTREGA", "FINALIZADO"),
                pedido.getHistorico().stream().map(HistoricoStatus::getStatusNovo).toList());
    }

    @Test
    void fluxoComRetiradaRegistraSaida() {
        Pedido pedido = pedidoSalvo("p1", requisicao(item("pizza", 1, null)));
        logarComo("gerente1", "ROLE_GERENTE");

        service.atualizarStatus("p1", "EM_PREPARACAO");
        service.atualizarStatus("p1", "PRONTO");
        service.atualizarStatus("p1", "RETIRADO");

        assertEquals("RETIRADO", pedido.getTipoSaida());
        assertNotNull(pedido.getDataSaida());
        service.atualizarStatus("p1", "FINALIZADO");
        assertEquals("FINALIZADO", pedido.getStatus());
    }

    @Test
    void recusaTransicoesInvalidas() {
        Pedido pedido = pedidoSalvo("p1", requisicao(item("pizza", 1, null)));
        logarComo("gerente1", "ROLE_GERENTE");

        // Pular etapas a partir de RECEBIDO
        for (String destino : List.of("PRONTO", "SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO", "RECEBIDO")) {
            assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", destino), "aceitou RECEBIDO→" + destino);
        }
        service.atualizarStatus("p1", "EM_PREPARACAO");
        // Exemplo do enunciado: finalizar pedido em preparação
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", "FINALIZADO"));
        // Voltar para trás
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", "RECEBIDO"));
        // CANCELADO não pode ser usado como status comum
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", "CANCELADO"));
        // Status inexistente
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", "QUALQUER"));
        assertEquals("EM_PREPARACAO", pedido.getStatus());
    }

    @Test
    void pedidoFinalizadoNaoAceitaMaisAlteracoes() {
        Pedido pedido = pedidoSalvo("p1", comDesconto(requisicao(item("pizza", 1, null)), null));
        logarComo("gerente1", "ROLE_GERENTE");
        for (String s : List.of("EM_PREPARACAO", "PRONTO", "RETIRADO", "FINALIZADO")) {
            service.atualizarStatus("p1", s);
        }

        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", "PRONTO"));
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "Cliente desistiu"));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", 10.0));
        assertEquals("FINALIZADO", pedido.getStatus());
    }

    // ===== Cancelamento =====

    @Test
    void cancelamentoExigeJustificativaEPreservaDados() {
        Pedido pedido = pedidoSalvo("p1", requisicao(item("pizza", 2, null), item("refri", 3, null)));
        logarComo("gerente1", "ROLE_GERENTE");
        service.atualizarStatus("p1", "EM_PREPARACAO");

        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", null));
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "   "));
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "ab"));
        assertEquals("EM_PREPARACAO", pedido.getStatus());

        service.cancelar("p1", "Cliente desistiu do pedido");

        assertEquals("CANCELADO", pedido.getStatus());
        assertEquals("Cliente desistiu do pedido", pedido.getJustificativaCancelamento());
        assertEquals("EM_PREPARACAO", pedido.getStatusAntesDoCancelamento());
        assertEquals("gerente1", pedido.getCanceladoPor());
        assertNotNull(pedido.getDataCancelamento());
        // Dados históricos preservados
        assertEquals(2, pedido.getItens().size());
        assertEquals(124.0, pedido.getValorFinal());
        assertEquals("atendente1", pedido.getCriadoPor());
        HistoricoStatus ultimo = pedido.getHistorico().get(pedido.getHistorico().size() - 1);
        assertEquals("CANCELADO", ultimo.getStatusNovo());
        assertEquals("Cliente desistiu do pedido", ultimo.getObservacao());
    }

    @Test
    void cancelamentoSoAntesDaSaida() {
        for (String ate : List.of("RECEBIDO", "EM_PREPARACAO", "PRONTO")) {
            Pedido pedido = pedidoSalvo("ok-" + ate, requisicao(item("pizza", 1, null)));
            logarComo("gerente1", "ROLE_GERENTE");
            avancarAte("ok-" + ate, ate);
            service.cancelar("ok-" + ate, "Justificativa válida");
            assertEquals("CANCELADO", pedido.getStatus(), "não cancelou em " + ate);
            logarComo("atendente1", "ROLE_ATENDENTE");
        }
        for (String ate : List.of("SAIU_PARA_ENTREGA", "RETIRADO")) {
            Pedido pedido = pedidoSalvo("x-" + ate, requisicao(item("pizza", 1, null)));
            logarComo("gerente1", "ROLE_GERENTE");
            avancarAte("x-" + ate, ate);
            assertThrows(RegraNegocioException.class, () -> service.cancelar("x-" + ate, "Justificativa válida"));
            assertEquals(ate, pedido.getStatus());
            logarComo("atendente1", "ROLE_ATENDENTE");
        }
    }

    @Test
    void pedidoCanceladoNaoAceitaNenhumaAlteracao() {
        Pedido pedido = pedidoSalvo("p1", comDesconto(requisicao(item("pizza", 1, null)), null));
        logarComo("gerente1", "ROLE_GERENTE");
        service.cancelar("p1", "Pedido duplicado");

        for (String s : List.of("RECEBIDO", "EM_PREPARACAO", "PRONTO", "SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO")) {
            assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", s), "alterou para " + s);
        }
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "Outra justificativa"));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", 10.0));
        logarComo("atendente1", "ROLE_ATENDENTE");
        assertThrows(RegraNegocioException.class, () -> service.solicitarCancelamento("p1", "motivo"));
        assertEquals("CANCELADO", pedido.getStatus());
        assertEquals(50.0, pedido.getValorFinal());
    }

    @Test
    void descontoSoAntesDaSaida() {
        Pedido pedido = pedidoSalvo("p1", comDesconto(requisicao(item("pizza", 2, null)), null));
        logarComo("gerente1", "ROLE_GERENTE");
        avancarAte("p1", "SAIU_PARA_ENTREGA");

        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", 10.0));
        assertEquals(100.0, pedido.getValorFinal());
    }

    @Test
    void aceitaStatusAntigosGravadosNoBanco() {
        Pedido pedido = pedidoSalvo("p1", requisicao(item("pizza", 1, null)));
        pedido.setStatus("EM_PREPARO"); // nome usado antes deste fluxo
        logarComo("gerente1", "ROLE_GERENTE");

        service.atualizarStatus("p1", "PRONTO");
        assertEquals("PRONTO", pedido.getStatus());

        pedido.setStatus("ENTREGUE"); // equivale a FINALIZADO
        assertTrue(pedido.isFinalizado());
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", "PRONTO"));
    }

    // ===== auxiliares de fluxo =====

    private Pedido pedidoSalvo(String id, NovoPedidoRequest req) {
        Pedido pedido = service.criarPedido(req);
        pedido.setId(id);
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));
        return pedido;
    }

    private void avancarAte(String id, String destino) {
        List<String> caminho = destino.equals("RETIRADO")
                ? List.of("EM_PREPARACAO", "PRONTO", "RETIRADO")
                : List.of("EM_PREPARACAO", "PRONTO", "SAIU_PARA_ENTREGA", "FINALIZADO");
        for (String s : caminho) {
            if (destino.equals("RECEBIDO")) {
                return;
            }
            service.atualizarStatus(id, s);
            if (s.equals(destino)) {
                return;
            }
        }
    }

    // ===== auxiliares =====

    private static Produto produto(String id, String nome, double preco, boolean ativo) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setPreco(preco);
        p.setAtivo(ativo);
        return p;
    }

    private static ItemPedidoRequest item(String produtoId, int quantidade, String sabor) {
        ItemPedidoRequest i = new ItemPedidoRequest();
        i.setProdutoId(produtoId);
        i.setQuantidade(quantidade);
        i.setSabor(sabor);
        return i;
    }

    private static NovoPedidoRequest requisicao(ItemPedidoRequest... itens) {
        NovoPedidoRequest r = new NovoPedidoRequest();
        r.setClienteId("c1");
        r.setItens(new java.util.ArrayList<>(List.of(itens)));
        return r;
    }

    private static NovoPedidoRequest comDesconto(NovoPedidoRequest r, String motivo) {
        r.setSolicitarDesconto(true);
        r.setMotivoDesconto(motivo);
        return r;
    }

    private static void logarComo(String usuario, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, List.of(new SimpleGrantedAuthority(role))));
    }
}
