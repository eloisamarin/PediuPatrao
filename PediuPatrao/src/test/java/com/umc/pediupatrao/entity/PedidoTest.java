package com.umc.pediupatrao.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PedidoTest {

    private static ItemPedido item(double preco, int quantidade) {
        ItemPedido item = new ItemPedido();
        item.setNomeProduto("Produto");
        item.setPrecoUnitario(preco);
        item.setQuantidade(quantidade);
        return item;
    }

    @Test
    void calculaSubtotaisEValorBrutoAPartirDosItens() {
        Pedido pedido = new Pedido();
        pedido.setItens(List.of(item(50.0, 2), item(8.0, 3)));

        pedido.recalcularTotais();

        assertEquals(100.0, pedido.getItens().get(0).getSubtotal());
        assertEquals(24.0, pedido.getItens().get(1).getSubtotal());
        assertEquals(124.0, pedido.getValorBruto());
        assertEquals(0.0, pedido.getValorDesconto());
        assertEquals(124.0, pedido.getValorFinal());
    }

    @Test
    void naoTemErroDeCentavosComValoresQuebrados() {
        Pedido pedido = new Pedido();
        // Com double puro, 0.1 + 0.2 daria 0.30000000000000004
        pedido.setItens(List.of(item(0.1, 1), item(0.2, 1), item(33.33, 3)));

        pedido.recalcularTotais();

        assertEquals(100.29, pedido.getValorBruto());
        assertEquals(100.29, pedido.getValorFinal());
    }

    @Test
    void aplicaDescontoAutorizadoComArredondamentoComercial() {
        Pedido pedido = new Pedido();
        pedido.setItens(List.of(item(33.33, 3))); // 99,99
        pedido.setDescontoPercentual(10.0);       // 9,999 -> 10,00

        pedido.recalcularTotais();

        assertEquals(99.99, pedido.getValorBruto());
        assertEquals(10.0, pedido.getValorDesconto());
        assertEquals(89.99, pedido.getValorFinal());
    }

    @Test
    void ignoraTotaisGravadosEmItensERecalculaTudo() {
        ItemPedido adulterado = item(50.0, 2);
        adulterado.setSubtotal(0.01); // valor inconsistente vindo de qualquer lugar
        Pedido pedido = new Pedido();
        pedido.setItens(List.of(adulterado));
        pedido.setValorBruto(1.0);
        pedido.setValorFinal(1.0);

        pedido.recalcularTotais();

        assertEquals(100.0, adulterado.getSubtotal());
        assertEquals(100.0, pedido.getValorFinal());
    }

    @Test
    void descontoDeCemPorCentoZeraOTotal() {
        Pedido pedido = new Pedido();
        pedido.setItens(List.of(item(42.5, 2)));
        pedido.setDescontoPercentual(100.0);

        pedido.recalcularTotais();

        assertEquals(0.0, pedido.getValorFinal());
    }
}
