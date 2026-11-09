package com.umc.pediupatrao.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Garante que os dados fictícios são coerentes com as regras do sistema. */
class GeradorDadosDemoTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 7, 20, 30);

    private GeradorDadosDemo.Dados gerar() {
        return new GeradorDadosDemo(2026L, AGORA, 50).gerar(8, Map.of());
    }

    @Test
    void volumeDeMedioPorteEmOitoMeses() {
        List<Pedido> pedidos = gerar().pedidos();
        // ~245 dias x ~50 pedidos/dia (com variação por dia da semana)
        assertTrue(pedidos.size() > 9_000 && pedidos.size() < 16_000, "pedidos: " + pedidos.size());
        assertTrue(pedidos.stream().allMatch(p -> !p.getDataCriacao().isBefore(AGORA.minusMonths(8).toLocalDate().atStartOfDay())));
    }

    @Test
    void nenhumPedidoNoFuturoETodosComIdDemo() {
        GeradorDadosDemo.Dados d = gerar();
        assertTrue(d.pedidos().stream().noneMatch(p -> p.getDataCriacao().isAfter(AGORA)));
        assertTrue(d.pedidos().stream().allMatch(p -> p.getId().startsWith(GeradorDadosDemo.PREFIXO)));
        assertTrue(d.clientes().stream().allMatch(c -> c.getId().startsWith(GeradorDadosDemo.PREFIXO)));
        assertTrue(d.produtos().stream().allMatch(p -> p.getId().startsWith(GeradorDadosDemo.PREFIXO)));
    }

    @Test
    void valoresBatemComOsItensEDesconto() {
        for (Pedido p : gerar().pedidos().subList(0, 500)) {
            double soma = p.getItens().stream().mapToDouble(i -> i.getPrecoUnitario() * i.getQuantidade()).sum();
            assertEquals(soma, p.getValorBruto(), 0.01);
            assertEquals(p.getValorBruto() - p.getValorDesconto(), p.getValorFinal(), 0.01);
        }
    }

    @Test
    void pedidosAntigosTerminamFinalizadosOuCancelados() {
        Map<StatusPedido, Long> porStatus = gerar().pedidos().stream()
                .filter(p -> p.getDataCriacao().isBefore(AGORA.minusDays(1)))
                .collect(Collectors.groupingBy(Pedido::getStatusAtual, Collectors.counting()));
        assertEquals(2, porStatus.size(), porStatus.toString());
        long total = porStatus.values().stream().mapToLong(Long::longValue).sum();
        double taxaCancelamento = porStatus.get(StatusPedido.CANCELADO) * 100.0 / total;
        assertTrue(taxaCancelamento > 2 && taxaCancelamento < 6, "cancelamento: " + taxaCancelamento);
    }

    @Test
    void historicoSegueOFluxoDoSistema() {
        for (Pedido p : gerar().pedidos().subList(0, 500)) {
            for (int i = 1; i < p.getHistorico().size(); i++) {
                var anterior = StatusPedido.valueOf(p.getHistorico().get(i).getStatusAnterior());
                var novo = StatusPedido.valueOf(p.getHistorico().get(i).getStatusNovo());
                assertTrue(anterior.podeIrPara(novo) || novo == StatusPedido.CANCELADO, anterior + " -> " + novo);
                assertFalse(p.getHistorico().get(i).getDataHora().isBefore(p.getHistorico().get(i - 1).getDataHora()));
            }
        }
    }

    @Test
    void fimDeSemanaVendeMaisQueSegunda() {
        Map<DayOfWeek, Long> porDia = gerar().pedidos().stream()
                .collect(Collectors.groupingBy(p -> p.getDataCriacao().getDayOfWeek(), Collectors.counting()));
        assertTrue(porDia.get(DayOfWeek.SATURDAY) > porDia.get(DayOfWeek.MONDAY) * 1.5);
    }

    @Test
    void reaproveitaProdutoJaCadastrado() {
        Produto coca = new Produto();
        coca.setId("id-real-da-coca");
        coca.setNome("Coca-Cola 2L");
        coca.setTipo("Bebida");
        coca.setPreco(14.0);
        GeradorDadosDemo.Dados d = new GeradorDadosDemo(1L, AGORA, 50).gerar(1, Map.of("coca-cola 2l", coca));
        assertTrue(d.produtos().stream().noneMatch(p -> p.getNome().equals("Coca-Cola 2L")));
        assertTrue(d.pedidos().stream().flatMap(p -> p.getItens().stream()).anyMatch(i -> i.getProdutoId().equals("id-real-da-coca")));
    }

    @Test
    void mesmaSementeGeraOsMesmosDados() {
        assertEquals(gerar().pedidos().size(), gerar().pedidos().size());
        assertEquals(gerar().pedidos().get(100).getValorFinal(), gerar().pedidos().get(100).getValorFinal());
    }
}
