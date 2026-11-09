package com.umc.pediupatrao.demo;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Gera dados FICTÍCIOS de uma pizzaria de médio porte, para demonstração e testes do dashboard.
 *
 * Classe "pura" (não acessa banco): recebe a data de hoje e uma semente aleatória e devolve
 * produtos, clientes e pedidos. A mesma semente gera sempre os mesmos dados.
 *
 * Todos os registros gerados têm o ID começando com "demo-". É assim que eles são encontrados
 * para remoção, sem tocar nos dados reais do sistema.
 *
 * Perfil simulado (médio porte):
 *  - cerca de 50 pedidos por dia, com sexta, sábado e domingo mais fortes;
 *  - picos no almoço (11h–14h) e principalmente no jantar (18h–23h, pico às 20h);
 *  - crescimento leve ao longo dos meses e datas especiais com mais movimento;
 *  - cerca de 4% de pedidos cancelados e 6% com desconto autorizado pelo gerente;
 *  - 65% entrega e 35% retirada no balcão.
 */
public class GeradorDadosDemo {

    public static final String PREFIXO = "demo-";

    private static final String[] ATENDENTES = { "atendente", "carla.atendente", "joao.atendente" };
    private static final String[] ENTREGADORES = { "motoboy.marcos", "motoboy.lucas", "motoboy.rafa" };
    private static final String GERENTE = "gerente";

    // Cardápio: nome, categoria, preço, peso (popularidade relativa)
    private static final Object[][] CARDAPIO = {
            { "Pizza Calabresa", "Pizza", 49.90, 18 }, { "Pizza Mussarela", "Pizza", 45.90, 15 },
            { "Pizza Portuguesa", "Pizza", 54.90, 11 }, { "Pizza Frango com Catupiry", "Pizza", 52.90, 13 },
            { "Pizza Margherita", "Pizza", 47.90, 7 }, { "Pizza Quatro Queijos", "Pizza", 56.90, 8 },
            { "Pizza Pepperoni", "Pizza", 58.90, 6 }, { "Pizza Chocolate", "Pizza", 44.90, 4 },
            { "Esfiha de Carne", "Esfiha", 6.50, 9 }, { "Esfiha de Queijo", "Esfiha", 6.90, 6 },
            { "Esfiha de Calabresa", "Esfiha", 6.90, 4 },
            { "X-Burguer", "Lanche", 26.90, 5 }, { "X-Salada", "Lanche", 28.90, 4 }, { "X-Bacon", "Lanche", 32.90, 4 },
            { "Coca-Cola 2L", "Bebida", 14.00, 14 }, { "Guaraná 2L", "Bebida", 12.00, 9 },
            { "Suco Natural 500ml", "Bebida", 9.00, 4 }, { "Água Mineral", "Bebida", 4.00, 3 },
            { "Cerveja Lata", "Bebida", 7.00, 5 },
            { "Petit Gâteau", "Sobremesa", 18.90, 3 }, { "Brownie", "Sobremesa", 12.90, 2 },
    };

    private static final String[] NOMES = { "Ana", "Bruno", "Carla", "Diego", "Eduarda", "Felipe", "Gabriela", "Henrique",
            "Isabela", "João", "Karina", "Lucas", "Mariana", "Nicolas", "Olívia", "Pedro", "Rafaela", "Samuel", "Tatiane",
            "Vinícius", "Larissa", "Gustavo", "Beatriz", "Matheus", "Juliana", "Thiago", "Camila", "Rodrigo", "Fernanda",
            "André", "Patrícia", "Marcelo", "Letícia", "Ricardo", "Aline", "Leonardo" };
    private static final String[] SOBRENOMES = { "Silva", "Santos", "Oliveira", "Souza", "Rodrigues", "Ferreira", "Alves",
            "Pereira", "Lima", "Gomes", "Costa", "Ribeiro", "Martins", "Carvalho", "Almeida", "Lopes", "Soares", "Fernandes",
            "Vieira", "Barbosa", "Rocha", "Dias", "Nascimento", "Moreira", "Mendes" };
    private static final String[] RUAS = { "Rua Dr. Deodato Wertheimer", "Av. Voluntário Fernando Pinheiro Franco",
            "Rua Ipiranga", "Rua Cel. Souza Franco", "Av. Ver. Narciso Yague Guimarães", "Rua Navajás", "Rua Barão de Jaceguai",
            "Av. Francisco Rodrigues Filho", "Rua Pres. Campos Salles", "Rua Ricardo Vilela" };
    private static final String[] BAIRROS = { "Centro", "Vila Oliveira", "Mogilar", "Vila Industrial", "Brás Cubas",
            "Jardim Universo", "Socorro", "Alto Ipiranga", "César de Souza", "Vila Mogilar" };
    private static final String[] MOTIVOS_CANCELAMENTO = { "Cliente desistiu do pedido", "Endereço fora da área de entrega",
            "Pedido duplicado", "Cliente não atendeu a confirmação", "Falta de ingrediente" };

    // Movimento relativo por dia da semana (segunda = índice 0)
    private static final double[] PESO_DIA = { 0.70, 0.75, 0.85, 0.95, 1.30, 1.45, 1.25 };
    // Distribuição dos pedidos por hora (0h–23h)
    private static final double[] PESO_HORA = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 6, 5, 2, 0, 0, 0, 7, 13, 18, 16, 10, 4 };

    private final Random r;
    private final LocalDateTime agora;
    private final int pedidosPorDia;

    public GeradorDadosDemo(long semente, LocalDateTime agora, int pedidosPorDia) {
        this.r = new Random(semente);
        this.agora = agora;
        this.pedidosPorDia = pedidosPorDia;
    }

    /** Resultado da geração. */
    public record Dados(List<Produto> produtos, List<Cliente> clientes, List<Pedido> pedidos) {
    }

    /**
     * @param meses             quantos meses para trás (a partir de hoje)
     * @param produtosExistentes produtos já cadastrados por nome (minúsculo), para reaproveitar em vez de duplicar
     */
    public Dados gerar(int meses, Map<String, Produto> produtosExistentes) {
        List<Produto> novos = new ArrayList<>();
        List<Produto> cardapio = new ArrayList<>();
        int[] pesos = new int[CARDAPIO.length];
        for (int i = 0; i < CARDAPIO.length; i++) {
            Object[] c = CARDAPIO[i];
            Produto p = produtosExistentes.get(((String) c[0]).toLowerCase());
            if (p == null) {
                p = new Produto();
                p.setId(PREFIXO + "prod-" + (i + 1));
                p.setNome((String) c[0]);
                p.setTipo((String) c[1]);
                p.setPreco((Double) c[2]);
                p.setAtivo(true);
                novos.add(p);
            }
            cardapio.add(p);
            pesos[i] = (Integer) c[3];
        }

        List<Cliente> clientes = gerarClientes(Math.max(50, pedidosPorDia * 25));

        List<Pedido> pedidos = new ArrayList<>();
        LocalDate inicio = agora.toLocalDate().minusMonths(meses);
        long totalDias = ChronoUnit.DAYS.between(inicio, agora.toLocalDate());
        int seq = 0;
        for (LocalDate dia = inicio; !dia.isAfter(agora.toLocalDate()); dia = dia.plusDays(1)) {
            long diaIndice = ChronoUnit.DAYS.between(inicio, dia);
            double crescimento = 0.85 + 0.30 * diaIndice / Math.max(1, totalDias); // ~85% no início → ~115% hoje
            double media = pedidosPorDia * PESO_DIA[dia.getDayOfWeek().getValue() - 1] * crescimento * fatorDataEspecial(dia);
            int quantidade = (int) Math.max(0, Math.round(media + r.nextGaussian() * Math.sqrt(media)));
            for (int i = 0; i < quantidade; i++) {
                LocalDateTime entrada = dia.atTime(sortear(PESO_HORA), r.nextInt(60), r.nextInt(60));
                if (entrada.isAfter(agora)) {
                    continue; // não cria pedido no futuro
                }
                pedidos.add(gerarPedido(PREFIXO + "ped-" + (++seq), entrada, cardapio, pesos, clientes));
            }
        }
        return new Dados(novos, clientes, pedidos);
    }

    // Datas com movimento maior (pizzaria costuma vender mais nelas)
    private static double fatorDataEspecial(LocalDate dia) {
        MonthDay md = MonthDay.from(dia);
        if (md.equals(MonthDay.of(6, 12))) return 1.8;   // Dia dos Namorados
        if (md.equals(MonthDay.of(7, 10))) return 1.6;   // Dia da Pizza
        if (md.equals(MonthDay.of(12, 24)) || md.equals(MonthDay.of(12, 31))) return 0.5;
        if (md.equals(MonthDay.of(12, 25)) || md.equals(MonthDay.of(1, 1))) return 1.4;
        if (dia.getDayOfWeek() == DayOfWeek.SATURDAY && dia.getDayOfMonth() <= 7) return 1.15; // sábado pós-pagamento
        return 1.0;
    }

    private List<Cliente> gerarClientes(int quantidade) {
        List<Cliente> lista = new ArrayList<>();
        for (int i = 1; i <= quantidade; i++) {
            Cliente c = new Cliente();
            c.setId(PREFIXO + "cli-" + i);
            c.setNome(NOMES[r.nextInt(NOMES.length)] + " " + SOBRENOMES[r.nextInt(SOBRENOMES.length)]);
            c.setTelefone(String.format("(11) 9%04d-%04d", r.nextInt(10000), r.nextInt(10000)));
            c.setLogradouro(RUAS[r.nextInt(RUAS.length)]);
            c.setNumero(String.valueOf(10 + r.nextInt(2000)));
            c.setBairro(BAIRROS[r.nextInt(BAIRROS.length)]);
            c.setCidade("Mogi das Cruzes");
            c.setEstado("SP");
            c.setCep(String.format("087%02d-%03d", r.nextInt(100), r.nextInt(1000)));
            lista.add(c);
        }
        return lista;
    }

    private Pedido gerarPedido(String id, LocalDateTime entrada, List<Produto> cardapio, int[] pesos, List<Cliente> clientes) {
        Pedido p = new Pedido();
        p.setId(id);
        // Clientes fiéis: os primeiros 20% da lista fazem boa parte dos pedidos
        int idx = r.nextDouble() < 0.5 ? r.nextInt(clientes.size() / 5) : r.nextInt(clientes.size());
        p.setClienteId(clientes.get(idx).getId());
        p.setCriadoPor(ATENDENTES[r.nextInt(ATENDENTES.length)]);
        p.setDataCriacao(entrada);

        // Itens: quase sempre uma ou duas pizzas, às vezes esfihas/lanches, normalmente com bebida
        List<ItemPedido> itens = new ArrayList<>();
        int linhas = 1 + (r.nextDouble() < 0.55 ? 1 : 0) + (r.nextDouble() < 0.25 ? 1 : 0) + (r.nextDouble() < 0.08 ? 1 : 0);
        for (int i = 0; i < linhas; i++) {
            Produto prod = cardapio.get(sortear(pesos));
            int qtd = "Esfiha".equals(prod.getTipo()) ? 4 + r.nextInt(9) : (r.nextDouble() < 0.8 ? 1 : 2);
            ItemPedido item = itens.stream().filter(it -> it.getProdutoId().equals(prod.getId())).findFirst().orElse(null);
            if (item != null) {
                item.setQuantidade(item.getQuantidade() + qtd);
                continue;
            }
            item = new ItemPedido();
            item.setProdutoId(prod.getId());
            item.setNomeProduto(prod.getNome());
            item.setPrecoUnitario(prod.getPreco());
            item.setQuantidade(qtd);
            itens.add(item);
        }
        p.setItens(itens);

        // Desconto autorizado pelo gerente em ~6% dos pedidos
        if (r.nextDouble() < 0.06) {
            double pct = new double[] { 5, 10, 10, 15 }[r.nextInt(4)];
            p.setDescontoPercentual(pct);
            p.setDescontoSolicitado(false);
            p.setMotivoDesconto("Cliente fidelidade");
            p.setDescontoSolicitadoPor(p.getCriadoPor());
            p.setDescontoAplicadoPor(GERENTE);
            p.setDataDesconto(entrada.plusMinutes(2));
        }
        p.recalcularTotais();

        simularFluxo(p, entrada);
        return p;
    }

    /** Percorre o fluxo de status até onde o tempo já permitiu (pedidos de hoje podem estar em andamento). */
    private void simularFluxo(Pedido p, LocalDateTime t) {
        String atual = StatusPedido.RECEBIDO.name();
        p.setStatus(atual);
        p.registrarHistorico(null, atual, t, p.getCriadoPor(), null);

        boolean entrega = r.nextDouble() < 0.65;
        boolean cancelar = r.nextDouble() < 0.04;

        LocalDateTime preparo = t.plusMinutes(2 + r.nextInt(7));
        LocalDateTime pronto = preparo.plusMinutes(14 + r.nextInt(14));
        LocalDateTime saida = pronto.plusMinutes(entrega ? 3 + r.nextInt(10) : 2 + r.nextInt(20));
        LocalDateTime fim = entrega ? saida.plusMinutes(12 + r.nextInt(25)) : saida.plusMinutes(1);

        if (cancelar) {
            LocalDateTime quando = t.plusMinutes(3 + r.nextInt(10));
            if (!quando.isAfter(agora)) {
                String motivo = MOTIVOS_CANCELAMENTO[r.nextInt(MOTIVOS_CANCELAMENTO.length)];
                p.setCancelamentoSolicitado(false);
                p.setMotivoCancelamento(motivo);
                p.setCancelamentoSolicitadoPor(p.getCriadoPor());
                p.setDataSolicitacaoCancelamento(quando.minusMinutes(1));
                p.setStatusAntesDoCancelamento(atual);
                p.setCanceladoPor(GERENTE);
                p.setDataCancelamento(quando);
                p.setJustificativaCancelamento(motivo);
                avancar(p, atual, StatusPedido.CANCELADO, quando, GERENTE, motivo);
                return;
            }
        }
        if (preparo.isAfter(agora)) return;
        atual = avancar(p, atual, StatusPedido.EM_PREPARACAO, preparo, GERENTE, null);
        if (pronto.isAfter(agora)) return;
        atual = avancar(p, atual, StatusPedido.PRONTO, pronto, GERENTE, null);
        if (saida.isAfter(agora)) return;

        StatusPedido tipoSaida = entrega ? StatusPedido.SAIU_PARA_ENTREGA : StatusPedido.RETIRADO;
        String responsavel = entrega ? ENTREGADORES[r.nextInt(ENTREGADORES.length)] : p.getCriadoPor();
        p.setDataSaida(saida);
        p.setResponsavelSaida(responsavel);
        p.setTipoSaida(tipoSaida.name());
        atual = avancar(p, atual, tipoSaida, saida, GERENTE, null);
        if (fim.isAfter(agora)) return;

        p.setDataFinalizacao(fim);
        p.setFinalizadoPor(GERENTE);
        avancar(p, atual, StatusPedido.FINALIZADO, fim, GERENTE, null);
    }

    private static String avancar(Pedido p, String de, StatusPedido para, LocalDateTime quando, String usuario, String obs) {
        p.setStatus(para.name());
        p.registrarHistorico(de, para.name(), quando, usuario, obs);
        return para.name();
    }

    /** Sorteia um índice proporcional aos pesos. */
    private int sortear(int[] pesos) {
        int total = 0;
        for (int p : pesos) total += p;
        int x = r.nextInt(total);
        for (int i = 0; i < pesos.length; i++) {
            x -= pesos[i];
            if (x < 0) return i;
        }
        return pesos.length - 1;
    }

    private int sortear(double[] pesos) {
        double total = 0;
        for (double p : pesos) total += p;
        double x = r.nextDouble() * total;
        for (int i = 0; i < pesos.length; i++) {
            x -= pesos[i];
            if (x < 0) return i;
        }
        return 20;
    }
}
