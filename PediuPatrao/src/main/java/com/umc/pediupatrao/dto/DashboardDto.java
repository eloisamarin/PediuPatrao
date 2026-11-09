package com.umc.pediupatrao.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Objetos de resposta da API do Dashboard (/api/dashboard/**).
 *
 * São "records" (Java 16+): classes imutáveis só com dados, que o Jackson converte direto em JSON.
 * A API devolve estes DTOs e NUNCA a entidade Pedido inteira — assim o painel recebe só o que precisa
 * (e dados internos, como motivos de cancelamento, não vazam).
 */
public final class DashboardDto {

    private DashboardDto() {
    }

    /** Filtros já validados que foram aplicados na consulta (devolvidos para o front mostrar). */
    public record FiltroAplicado(LocalDate inicio, LocalDate fim, String status, String categoria) {
    }

    /** RF01 — Indicadores gerais (cards do topo). */
    public record Resumo(
            FiltroAplicado filtro,
            double faturamento,              // soma da receita líquida dos pedidos não cancelados
            long totalPedidos,               // todos os pedidos do período (inclui cancelados)
            long pedidosValidos,             // pedidos não cancelados
            long pedidosCancelados,
            double ticketMedio,              // faturamento / pedidos válidos
            double taxaCancelamento,         // % de pedidos cancelados
            long itensVendidos,              // unidades vendidas (pedidos não cancelados)
            double totalDescontos,           // R$ concedidos em desconto
            Double tempoMedioSaidaMinutos,   // média entre entrada e saída (null se não houver dados)
            Comparativo comparativo) {       // variação em relação ao período anterior de mesmo tamanho
    }

    /** Variação percentual em relação ao período anterior (null quando o anterior é zero). */
    public record Comparativo(LocalDate inicioAnterior, LocalDate fimAnterior,
            double faturamentoAnterior, long pedidosAnteriores,
            Double variacaoFaturamento, Double variacaoPedidos) {
    }

    /** RF02 — Um ponto da série temporal de vendas (por dia, semana ou mês). */
    public record PontoVenda(LocalDate periodo, String rotulo, double faturamento, long pedidos) {
    }

    /** RF02 — Vendas por hora do dia (identifica horários de pico). */
    public record VendaPorHora(int hora, double faturamento, long pedidos) {
    }

    /** RF02 — Vendas por categoria de produto (Pizza, Esfiha, Bebida...). */
    public record VendaPorCategoria(String categoria, double faturamento, long quantidade) {
    }

    /** RF03 — Quantidade de pedidos em cada status. */
    public record StatusQuantidade(String status, String descricao, long quantidade, double percentual) {
    }

    /** RF04 — Ranking de produtos mais vendidos. */
    public record ProdutoRanking(int posicao, String produtoId, String nome, String categoria,
            long quantidade, double faturamento) {
    }

    /** RF03 — Últimos pedidos do período (tabela). */
    public record PedidoRecente(String id, LocalDateTime dataCriacao, String cliente, String itens,
            String status, String descricaoStatus, double valor) {
    }

    /** RF05 — Opções disponíveis para montar os filtros na tela. */
    public record OpcoesFiltro(List<Opcao> status, List<String> categorias, LocalDate inicioPadrao,
            LocalDate fimPadrao, int diasMaximos) {
    }

    public record Opcao(String valor, String descricao) {
    }
}
