package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.DashboardDto.PontoVenda;
import com.umc.pediupatrao.dto.DashboardDto.ProdutoRanking;
import com.umc.pediupatrao.dto.DashboardDto.StatusQuantidade;
import com.umc.pediupatrao.dto.DashboardDto.VendaPorCategoria;
import com.umc.pediupatrao.dto.DashboardDto.VendaPorHora;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.TreeMap;

/**
  - Pedido CANCELADO não conta como venda (não entra em faturamento, itens vendidos, ranking).
 - Receita líquida de um item = subtotal x (1 - desconto% do pedido). Assim, ao filtrar por
     categoria, o faturamento soma só os itens daquela categoria, já com o desconto proporcional.
   - Pedidos antigos (de um produto só, sem lista de itens) viram um item equivalente.
   - Status antigos gravados no banco (EM_PREPARO, ENTREGUE) são convertidos pelo StatusPedido.de().
 */
public class DashboardCalculadora {

    public static final String SEM_CATEGORIA = "Sem categoria";
    private static final DateTimeFormatter ROTULO_DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter ROTULO_MES = DateTimeFormatter.ofPattern("MMM/yy", Locale.of("pt", "BR"));

    public enum Agrupamento { DIA, SEMANA, MES }

    /// Categoria e receita líquida calculada
    record Linha(String produtoId, String nome, String categoria, int quantidade, BigDecimal receita, BigDecimal desconto) {
    }

    /// O pedido com as linhas que passaram no filtro de categoria
    record Venda(Pedido pedido, StatusPedido status, List<Linha> linhas) {
        boolean cancelado() {
            return status == StatusPedido.CANCELADO;
        }

        BigDecimal receita() {
            return linhas.stream().map(Linha::receita).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        long unidades() {
            return linhas.stream().mapToLong(Linha::quantidade).sum();
        }
    }

    private final List<Venda> vendas;

    /**
     * @param pedidos            Filtro pela data já dos pedidos foi feito
     * @param categoriaDoProduto Mapa produtoId → categoria
     * @param status             Filtro de status
     * @param categoria          Filtro de categoria
     */
    public DashboardCalculadora(List<Pedido> pedidos, Map<String, String> categoriaDoProduto,
            StatusPedido status, String categoria) {
        this.vendas = new ArrayList<>();
        for (Pedido pedido : pedidos) {
            StatusPedido statusPedido = StatusPedido.de(pedido.getStatus()).orElse(null);
            if (status != null && statusPedido != status) {
                continue;
            }
            List<Linha> linhas = linhasDo(pedido, categoriaDoProduto).stream()
                    .filter(l -> categoria == null || l.categoria().equalsIgnoreCase(categoria))
                    .toList();
            // Com filtro de categoria, só entram pedidos que têm pelo menos um item daquela categoria
            if (categoria != null && linhas.isEmpty()) {
                continue;
            }
            vendas.add(new Venda(pedido, statusPedido, linhas));
        }
    }

    // =====================  Indicadores =====================

    public long totalPedidos() {
        return vendas.size();
    }

    public long pedidosCancelados() {
        return vendas.stream().filter(Venda::cancelado).count();
    }

    public long pedidosValidos() {
        return totalPedidos() - pedidosCancelados();
    }

    public double faturamento() {
        return dinheiro(validas().stream().map(Venda::receita).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public double ticketMedio() {
        long validos = pedidosValidos();
        if (validos == 0) {
            return 0;
        }
        return dinheiro(BigDecimal.valueOf(faturamento()).divide(BigDecimal.valueOf(validos), 2, RoundingMode.HALF_UP));
    }

    public double taxaCancelamento() {
        return percentual(pedidosCancelados(), totalPedidos());
    }

    public long itensVendidos() {
        return validas().stream().mapToLong(Venda::unidades).sum();
    }

    public double totalDescontos() {
        return dinheiro(validas().stream().flatMap(v -> v.linhas().stream())
                .map(Linha::desconto).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /// Tempo médio (em minutos) entre a entrada do pedido e a saída (entrega ou retirada).
    public Double tempoMedioSaidaMinutos() {
        OptionalDouble media = validas().stream()
                .map(Venda::pedido)
                .filter(p -> p.getDataCriacao() != null && p.getDataSaida() != null)
                .mapToLong(p -> Duration.between(p.getDataCriacao(), p.getDataSaida()).toMinutes())
                .filter(min -> min >= 0)
                .average();
        return media.isPresent() ? Math.round(media.getAsDouble() * 10) / 10.0 : null;
    }

    // =====================  Análise de vendas =====================


    public List<PontoVenda> serieDeVendas(LocalDate inicio, LocalDate fim, Agrupamento agrupamento) {
        Map<LocalDate, BigDecimal> faturamento = new TreeMap<>();
        Map<LocalDate, Long> pedidos = new TreeMap<>();
        for (LocalDate d = chave(inicio, agrupamento); !d.isAfter(fim); d = proximo(d, agrupamento)) {
            faturamento.put(d, BigDecimal.ZERO);
            pedidos.put(d, 0L);
        }
        for (Venda v : validas()) {
            if (v.pedido().getDataCriacao() == null) {
                continue;
            }
            LocalDate k = chave(v.pedido().getDataCriacao().toLocalDate(), agrupamento);
            faturamento.merge(k, v.receita(), BigDecimal::add);
            pedidos.merge(k, 1L, Long::sum);
        }
        List<PontoVenda> serie = new ArrayList<>();
        faturamento.forEach((k, valor) -> serie.add(new PontoVenda(k, rotulo(k, agrupamento), dinheiro(valor), pedidos.get(k))));
        return serie;
    }

    /// Vendas agrupadas pela hora do dia
    public List<VendaPorHora> vendasPorHora() {
        BigDecimal[] valor = new BigDecimal[24];
        long[] qtd = new long[24];
        java.util.Arrays.fill(valor, BigDecimal.ZERO);
        for (Venda v : validas()) {
            if (v.pedido().getDataCriacao() == null) {
                continue;
            }
            int h = v.pedido().getDataCriacao().getHour();
            valor[h] = valor[h].add(v.receita());
            qtd[h]++;
        }
        List<VendaPorHora> lista = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            lista.add(new VendaPorHora(h, dinheiro(valor[h]), qtd[h]));
        }
        return lista;
    }

    /// Faturamento e unidades por categoria, do maior para o menor faturamento.
    public List<VendaPorCategoria> vendasPorCategoria() {
        Map<String, BigDecimal> valor = new LinkedHashMap<>();
        Map<String, Long> qtd = new LinkedHashMap<>();
        validas().stream().flatMap(v -> v.linhas().stream()).forEach(l -> {
            valor.merge(l.categoria(), l.receita(), BigDecimal::add);
            qtd.merge(l.categoria(), (long) l.quantidade(), Long::sum);
        });
        return valor.entrySet().stream()
                .map(e -> new VendaPorCategoria(e.getKey(), dinheiro(e.getValue()), qtd.get(e.getKey())))
                .sorted(Comparator.comparingDouble(VendaPorCategoria::faturamento).reversed())
                .toList();
    }

    // ===================== Situação dos pedidos =====================

    /// Quantidade por status
    public List<StatusQuantidade> pedidosPorStatus() {
        Map<StatusPedido, Long> contagem = new EnumMap<>(StatusPedido.class);
        for (StatusPedido s : StatusPedido.values()) {
            contagem.put(s, 0L);
        }
        vendas.stream().map(Venda::status).filter(Objects::nonNull).forEach(s -> contagem.merge(s, 1L, Long::sum));
        long total = totalPedidos();
        return contagem.entrySet().stream()
                .map(e -> new StatusQuantidade(e.getKey().name(), e.getKey().getDescricao(), e.getValue(),
                        percentual(e.getValue(), total)))
                .toList();
    }

    /// Pedidos do período, do mais recente para o mais antigo (inclui cancelados)
    public List<Pedido> pedidosMaisRecentes(int limite) {
        return vendas.stream().map(Venda::pedido)
                .sorted(Comparator.comparing(Pedido::getDataCriacao, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limite)
                .toList();
    }

    /// Valor exibido para um pedido por categoria
    public double valorDoPedido(Pedido pedido) {
        return vendas.stream().filter(v -> v.pedido() == pedido).findFirst()
                .map(v -> dinheiro(v.receita())).orElse(0.0);
    }

    // =====================  Produtos mais vendidos =====================

    /// Ranking por quantidade vendida
    public List<ProdutoRanking> produtosMaisVendidos(int limite) {
        record Acumulado(String nome, String categoria, long quantidade, BigDecimal receita) {
            Acumulado somar(Linha l) {
                return new Acumulado(nome, categoria, quantidade + l.quantidade(), receita.add(l.receita()));
            }
        }
        Map<String, Acumulado> porProduto = new LinkedHashMap<>();
        validas().stream().flatMap(v -> v.linhas().stream()).forEach(l -> {
            // Agrupa pelo id do produto; se não houver id (dado antigo), agrupa pelo nome
            String chave = l.produtoId() != null ? l.produtoId() : "nome:" + l.nome();
            porProduto.merge(chave, new Acumulado(l.nome(), l.categoria(), l.quantidade(), l.receita()),
                    (atual, novo) -> atual.somar(l));
        });
        List<Map.Entry<String, Acumulado>> ordenado = porProduto.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Acumulado>>comparingLong(e -> e.getValue().quantidade()).reversed()
                        .thenComparing(e -> e.getValue().receita(), Comparator.reverseOrder()))
                .limit(limite)
                .toList();
        List<ProdutoRanking> ranking = new ArrayList<>();
        for (int i = 0; i < ordenado.size(); i++) {
            var e = ordenado.get(i);
            String id = e.getKey().startsWith("nome:") ? null : e.getKey();
            ranking.add(new ProdutoRanking(i + 1, id, e.getValue().nome(), e.getValue().categoria(),
                    e.getValue().quantidade(), dinheiro(e.getValue().receita())));
        }
        return ranking;
    }

    // ===================== Auxiliares =====================

    private List<Venda> validas() {
        return vendas.stream().filter(v -> !v.cancelado()).toList();
    }

    /// Converte os itens do pedido em linhas normalizadas (inclui o formato antigo de produto único)
    static List<Linha> linhasDo(Pedido pedido, Map<String, String> categoriaDoProduto) {
        BigDecimal percentualDesconto = pedido.getDescontoPercentual() == null
                ? BigDecimal.ZERO : BigDecimal.valueOf(pedido.getDescontoPercentual());
        List<Linha> linhas = new ArrayList<>();
        if (pedido.getItens() != null && !pedido.getItens().isEmpty()) {
            for (ItemPedido item : pedido.getItens()) {
                BigDecimal subtotal = item.getSubtotal() != null ? BigDecimal.valueOf(item.getSubtotal())
                        : BigDecimal.valueOf(item.getPrecoUnitario() == null ? 0 : item.getPrecoUnitario())
                                .multiply(BigDecimal.valueOf(item.getQuantidade()));
                BigDecimal desconto = subtotal.multiply(percentualDesconto).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                linhas.add(new Linha(item.getProdutoId(), nomeOuPadrao(item.getNomeProduto()),
                        categoriaDoProduto.getOrDefault(item.getProdutoId(), SEM_CATEGORIA),
                        item.getQuantidade(), subtotal.subtract(desconto), desconto));
            }
        } else if (pedido.getTipoPizza() != null || pedido.getProdutoId() != null) {
            // Pedido antigo: um produto só, com o valor gravado no próprio pedido
            BigDecimal bruto = BigDecimal.valueOf(pedido.getValorBruto() == null ? 0 : pedido.getValorBruto());
            BigDecimal liquido = pedido.getValorFinal() == null ? bruto : BigDecimal.valueOf(pedido.getValorFinal());
            linhas.add(new Linha(pedido.getProdutoId(), nomeOuPadrao(pedido.getTipoPizza()),
                    categoriaDoProduto.getOrDefault(pedido.getProdutoId(), SEM_CATEGORIA),
                    Math.max(pedido.getQuantidade(), 1), liquido, bruto.subtract(liquido).max(BigDecimal.ZERO)));
        }
        return linhas;
    }

    private static String nomeOuPadrao(String nome) {
        return nome == null || nome.isBlank() ? "Produto sem nome" : nome;
    }

    static LocalDate chave(LocalDate data, Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA -> data;
            case SEMANA -> data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> data.withDayOfMonth(1);
        };
    }

    private static LocalDate proximo(LocalDate data, Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA -> data.plusDays(1);
            case SEMANA -> data.plusWeeks(1);
            case MES -> data.plusMonths(1);
        };
    }

    private static String rotulo(LocalDate data, Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA -> data.format(ROTULO_DIA);
            case SEMANA -> "Sem. " + data.format(ROTULO_DIA);
            case MES -> data.format(ROTULO_MES);
        };
    }

    static double dinheiro(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    static double percentual(long parte, long total) {
        if (total == 0) {
            return 0;
        }
        return BigDecimal.valueOf(parte * 100.0 / total).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
