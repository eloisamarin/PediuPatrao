package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.umc.pediupatrao.dto.DashboardDto.PontoVenda;
import com.umc.pediupatrao.dto.DashboardDto.ProdutoRanking;
import com.umc.pediupatrao.dto.DashboardDto.StatusQuantidade;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.service.DashboardCalculadora.Agrupamento;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Regras de cálculo dos indicadores do dashboard (sem banco, sem Spring). */
class DashboardCalculadoraTest {

    private static final Map<String, String> CATEGORIAS = Map.of("p1", "Pizza", "p2", "Pizza", "b1", "Bebida");

    private static ItemPedido item(String produtoId, String nome, double preco, int qtd) {
        ItemPedido i = new ItemPedido();
        i.setProdutoId(produtoId);
        i.setNomeProduto(nome);
        i.setPrecoUnitario(preco);
        i.setQuantidade(qtd);
        return i;
    }

    private static Pedido pedido(StatusPedido status, LocalDateTime data, Double desconto, ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setStatus(status.name());
        p.setDataCriacao(data);
        p.setDescontoPercentual(desconto);
        p.setItens(List.of(itens));
        p.recalcularTotais();
        return p;
    }

    private final LocalDateTime dia1 = LocalDateTime.of(2026, 11, 1, 19, 30);
    private final LocalDateTime dia3 = LocalDateTime.of(2026, 11, 3, 20, 10);

    private final List<Pedido> pedidos = List.of(
            // 2 pizzas calabresa (2 x 50) + 1 refri (10) = 110, com 10% de desconto = 99
            pedido(StatusPedido.FINALIZADO, dia1, 10.0, item("p1", "Calabresa", 50, 2), item("b1", "Refri", 10, 1)),
            // 1 pizza mussarela = 45
            pedido(StatusPedido.EM_PREPARACAO, dia3, null, item("p2", "Mussarela", 45, 1)),
            // Cancelado: não conta como venda
            pedido(StatusPedido.CANCELADO, dia3, null, item("p1", "Calabresa", 50, 5)));

    @Test
    void indicadoresIgnoramPedidosCancelados() {
        DashboardCalculadora c = new DashboardCalculadora(pedidos, CATEGORIAS, null, null);

        assertEquals(3, c.totalPedidos());
        assertEquals(2, c.pedidosValidos());
        assertEquals(1, c.pedidosCancelados());
        assertEquals(144.0, c.faturamento());   // 99 + 45
        assertEquals(72.0, c.ticketMedio());    // 144 / 2
        assertEquals(33.3, c.taxaCancelamento());
        assertEquals(4, c.itensVendidos());     // 2 + 1 + 1
        assertEquals(11.0, c.totalDescontos()); // 10% de 110
    }

    @Test
    void filtroDeCategoriaSomaSoOsItensDaCategoriaComDescontoProporcional() {
        DashboardCalculadora c = new DashboardCalculadora(pedidos, CATEGORIAS, null, "Bebida");

        assertEquals(1, c.totalPedidos());
        assertEquals(9.0, c.faturamento()); // refri 10 - 10%
        assertEquals(1, c.itensVendidos());
    }

    @Test
    void filtroDeStatus() {
        DashboardCalculadora c = new DashboardCalculadora(pedidos, CATEGORIAS, StatusPedido.EM_PREPARACAO, null);
        assertEquals(1, c.totalPedidos());
        assertEquals(45.0, c.faturamento());
    }

    @Test
    void rankingPorQuantidadeSemCancelados() {
        List<ProdutoRanking> ranking = new DashboardCalculadora(pedidos, CATEGORIAS, null, null).produtosMaisVendidos(5);

        assertEquals("Calabresa", ranking.get(0).nome());
        assertEquals(2, ranking.get(0).quantidade()); // os 5 do pedido cancelado não contam
        assertEquals(90.0, ranking.get(0).faturamento());
        assertEquals(3, ranking.size());
    }

    @Test
    void serieDiariaPreencheDiasSemVendaComZero() {
        List<PontoVenda> serie = new DashboardCalculadora(pedidos, CATEGORIAS, null, null)
                .serieDeVendas(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), Agrupamento.DIA);

        assertEquals(3, serie.size());
        assertEquals(99.0, serie.get(0).faturamento());
        assertEquals(0, serie.get(1).pedidos());
        assertEquals(45.0, serie.get(2).faturamento());
    }

    @Test
    void statusTrazTodosOsStatusNaOrdemDoFluxo() {
        List<StatusQuantidade> status = new DashboardCalculadora(pedidos, CATEGORIAS, null, null).pedidosPorStatus();

        assertEquals(StatusPedido.values().length, status.size());
        assertEquals("RECEBIDO", status.get(0).status());
        assertEquals(1, status.stream().filter(s -> s.status().equals("CANCELADO")).findFirst().orElseThrow().quantidade());
    }

    @Test
    void semPedidosNaoDividePorZero() {
        DashboardCalculadora c = new DashboardCalculadora(List.of(), CATEGORIAS, null, null);
        assertEquals(0.0, c.ticketMedio());
        assertEquals(0.0, c.taxaCancelamento());
        assertNull(c.tempoMedioSaidaMinutos());
    }

    @Test
    void pedidoAntigoDeProdutoUnicoEStatusAntigo() {
        Pedido antigo = new Pedido();
        antigo.setStatus("ENTREGUE"); // nome antigo de FINALIZADO
        antigo.setProdutoId("p1");
        antigo.setTipoPizza("Calabresa");
        antigo.setQuantidade(3);
        antigo.setValorBruto(120.0);
        antigo.setDataCriacao(dia1);

        DashboardCalculadora c = new DashboardCalculadora(List.of(antigo), CATEGORIAS, null, "Pizza");
        assertEquals(120.0, c.faturamento());
        assertEquals(3, c.itensVendidos());
    }
}
