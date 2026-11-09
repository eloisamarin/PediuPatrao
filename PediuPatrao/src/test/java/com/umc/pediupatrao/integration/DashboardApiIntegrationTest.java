package com.umc.pediupatrao.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testes de INTEGRAÇÃO da API do Dashboard.
 *
 * Diferente dos testes unitários, aqui nada é simulado (mock):
 *  - a aplicação Spring Boot sobe inteira, numa porta aleatória (RANDOM_PORT);
 *  - o banco é um MongoDB 7 real, criado num container Docker só para o teste (Testcontainers) e
 *    apagado no final. O banco de produção (Atlas) NUNCA é usado;
 *  - as requisições são HTTP de verdade (TestRestTemplate), passando pelo Spring Security,
 *    pelo filtro da chave X-API-KEY, pelo controller, pelo service, pelo repositório e pelo banco.
 *
 * Os dados de teste ficam numa semana fixa (01 a 07/11/2026) e as consultas informam inicio/fim,
 * então o resultado não depende da data em que o teste é executado.
 *
 * Sem Docker em execução, a classe é ignorada (disabledWithoutDocker) em vez de falhar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "pediupatrao.dashboard.api-key=" + DashboardApiIntegrationTest.CHAVE,
        "pediupatrao.usuarios-demo=false",
        "pediupatrao.dados-demo.acao="
})
@Testcontainers(disabledWithoutDocker = true)
class DashboardApiIntegrationTest {

    static final String CHAVE = "chave-de-integracao";
    private static final String SEMANA = "inicio=2026-11-01&fim=2026-11-07";

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    // Aponta a aplicação para o container (sobrescreve a URL do Atlas do application.properties)
    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        registro.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("pediupatrao_teste"));
        registro.add("spring.data.mongodb.database", () -> "pediupatrao_teste");
    }

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private ObjectMapper json;
    @Value("${spring.data.mongodb.uri}")
    private String uriDoBanco;

    // ===================== Massa de dados =====================

    /**
     * Semana 01–07/11/2026:
     *  P1 01/11 19:30 FINALIZADO    2x Calabresa (50) + 1x Refri (10) = 110, desconto 10% → 99. Saída em 30 min
     *  P2 03/11 20:10 EM_PREPARACAO 1x Mussarela (45). Cliente que não existe no cadastro
     *  P3 03/11 21:00 CANCELADO     5x Calabresa = 250 (não pode contar como venda)
     *  P5 07/11 23:59:59 FINALIZADO 1x Refri = 10 (limite final do período: deve entrar)
     *  P6 08/11 00:00:00 FINALIZADO 1x Calabresa (fora do período: não pode entrar)
     * Semana anterior (25–31/10):
     *  P7 28/10 20:00 FINALIZADO    2x Calabresa = 100
     */
    @BeforeEach
    void prepararBanco() {
        mongo.dropCollection(Pedido.class);
        mongo.dropCollection(Produto.class);
        mongo.dropCollection(Cliente.class);
        mongo.indexOps("pedidos").ensureIndex(new org.springframework.data.mongodb.core.index.Index()
                .on("dataCriacao", org.springframework.data.domain.Sort.Direction.ASC).named("idx_dataCriacao"));

        mongo.insertAll(List.of(produto("p-calab", "Pizza Calabresa", "Pizza", 50.0),
                produto("p-mus", "Pizza Mussarela", "Pizza", 45.0),
                produto("b-refri", "Refrigerante 2L", "Bebida", 10.0)));

        Cliente ana = new Cliente();
        ana.setId("c1");
        ana.setNome("Ana Souza");
        mongo.insert(ana);

        Pedido p1 = pedido("P1", "c1", "FINALIZADO", LocalDateTime.of(2026, 11, 1, 19, 30), 10.0,
                item("p-calab", "Pizza Calabresa", 50, 2), item("b-refri", "Refrigerante 2L", 10, 1));
        p1.setDataSaida(LocalDateTime.of(2026, 11, 1, 20, 0));
        mongo.insertAll(List.of(p1,
                pedido("P2", "cliente-apagado", "EM_PREPARACAO", LocalDateTime.of(2026, 11, 3, 20, 10), null,
                        item("p-mus", "Pizza Mussarela", 45, 1)),
                pedido("P3", "c1", "CANCELADO", LocalDateTime.of(2026, 11, 3, 21, 0), null,
                        item("p-calab", "Pizza Calabresa", 50, 5)),
                pedido("P5", "c1", "FINALIZADO", LocalDateTime.of(2026, 11, 7, 23, 59, 59), null,
                        item("b-refri", "Refrigerante 2L", 10, 1)),
                pedido("P6", "c1", "FINALIZADO", LocalDateTime.of(2026, 11, 8, 0, 0, 0), null,
                        item("p-calab", "Pizza Calabresa", 50, 1)),
                pedido("P7", "c1", "FINALIZADO", LocalDateTime.of(2026, 10, 28, 20, 0), null,
                        item("p-calab", "Pizza Calabresa", 50, 2))));
    }

    // ===================== Ambiente =====================

    @Test
    void ambiente_usaMongoDoContainerENaoOAtlas() {
        assertFalse(uriDoBanco.contains("mongodb.net"), "o teste não pode usar o banco de produção");
        assertTrue(MONGO.isRunning());
    }

    // ===================== GET /resumo (RF01) =====================

    @Test
    void resumo_semanaComDados_deveCalcularIndicadoresAPartirDoBanco() throws Exception {
        ResponseEntity<String> r = get("/resumo?" + SEMANA);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        JsonNode d = json.readTree(r.getBody());

        assertEquals(154.0, d.get("faturamento").asDouble(), 0.001);       // 99 + 45 + 10 (sem o cancelado)
        assertEquals(4, d.get("totalPedidos").asInt());                     // P1, P2, P3, P5 (P6 fica fora)
        assertEquals(3, d.get("pedidosValidos").asInt());
        assertEquals(1, d.get("pedidosCancelados").asInt());
        assertEquals(51.33, d.get("ticketMedio").asDouble(), 0.001);        // 154 / 3
        assertEquals(25.0, d.get("taxaCancelamento").asDouble(), 0.001);    // 1 de 4
        assertEquals(5, d.get("itensVendidos").asInt());                    // 2 + 1 + 1 + 1
        assertEquals(11.0, d.get("totalDescontos").asDouble(), 0.001);      // 10% de 110
        assertEquals(30.0, d.get("tempoMedioSaidaMinutos").asDouble(), 0.001);
        assertEquals("2026-11-01", d.at("/filtro/inicio").asText());
    }

    @Test
    void resumo_deveCompararComPeriodoAnteriorDeMesmoTamanho() throws Exception {
        JsonNode c = json.readTree(get("/resumo?" + SEMANA).getBody()).get("comparativo");

        assertEquals("2026-10-25", c.get("inicioAnterior").asText());
        assertEquals("2026-10-31", c.get("fimAnterior").asText());
        assertEquals(100.0, c.get("faturamentoAnterior").asDouble(), 0.001);
        assertEquals(54.0, c.get("variacaoFaturamento").asDouble(), 0.001);  // (154 - 100) / 100
        assertEquals(200.0, c.get("variacaoPedidos").asDouble(), 0.001);     // 3 contra 1
    }

    @Test
    void resumo_limitesDoPeriodo_incluiUltimoSegundoDoFimEExcluiDiaSeguinte() throws Exception {
        JsonNode d = json.readTree(get("/resumo?inicio=2026-11-07&fim=2026-11-07").getBody());
        assertEquals(1, d.get("totalPedidos").asInt()); // só o P5 (23:59:59); o P6 (08/11 00:00) não entra
        assertEquals(10.0, d.get("faturamento").asDouble(), 0.001);
    }

    @Test
    void resumo_pedidoAntigoComStatusAntigo_deveSerConvertido() throws Exception {
        Pedido antigo = new Pedido();
        antigo.setId("P8");
        antigo.setStatus("ENTREGUE"); // nome antigo de FINALIZADO
        antigo.setProdutoId("p-calab");
        antigo.setTipoPizza("Calabresa");
        antigo.setQuantidade(3);
        antigo.setValorBruto(120.0);
        antigo.setDataCriacao(LocalDateTime.of(2026, 11, 5, 12, 0));
        mongo.insert(antigo);

        JsonNode d = json.readTree(get("/resumo?inicio=2026-11-05&fim=2026-11-05").getBody());
        assertEquals(120.0, d.get("faturamento").asDouble(), 0.001);
        assertEquals(3, d.get("itensVendidos").asInt());
    }

    // ===================== Filtros (RF05) =====================

    @Test
    void filtroCategoria_deveSomarSoItensDaCategoriaComDescontoProporcional() throws Exception {
        JsonNode d = json.readTree(get("/resumo?" + SEMANA + "&categoria=bebida").getBody());
        assertEquals(2, d.get("totalPedidos").asInt());                 // P1 e P5 têm bebida
        assertEquals(19.0, d.get("faturamento").asDouble(), 0.001);     // 10 - 10% + 10
        assertEquals("Bebida", d.at("/filtro/categoria").asText());     // normalizado pelo cadastro
    }

    @Test
    void filtroStatus_comNomeAntigo_deveSerNormalizado() throws Exception {
        JsonNode d = json.readTree(get("/resumo?" + SEMANA + "&status=em_preparo").getBody());
        assertEquals("EM_PREPARACAO", d.at("/filtro/status").asText());
        assertEquals(1, d.get("totalPedidos").asInt());
        assertEquals(45.0, d.get("faturamento").asDouble(), 0.001);
    }

    @Test
    void filtros_deveListarCategoriasCadastradasNoBanco() throws Exception {
        JsonNode d = json.readTree(get("/filtros").getBody());
        assertEquals("[\"Bebida\",\"Pizza\"]", d.get("categorias").toString());
        assertEquals(7, d.get("status").size());
        assertEquals(366, d.get("diasMaximos").asInt());
    }

    // ===================== GET /vendas, /vendas-por-hora, /vendas-por-categoria (RF02) =====================

    @Test
    void vendasPorDia_devePreencherDiasSemVendaComZero() throws Exception {
        JsonNode serie = json.readTree(get("/vendas?" + SEMANA + "&agrupamento=DIA").getBody());
        assertEquals(7, serie.size());
        assertEquals("01/11", serie.get(0).get("rotulo").asText());
        assertEquals(99.0, serie.get(0).get("faturamento").asDouble(), 0.001);
        assertEquals(0, serie.get(1).get("pedidos").asInt());                  // 02/11 sem pedidos
        assertEquals(45.0, serie.get(2).get("faturamento").asDouble(), 0.001); // 03/11 sem o cancelado
        assertEquals(10.0, serie.get(6).get("faturamento").asDouble(), 0.001);
    }

    @Test
    void vendasPorMes_deveAgruparPorMes() throws Exception {
        JsonNode serie = json.readTree(get("/vendas?inicio=2026-10-01&fim=2026-11-07&agrupamento=MES").getBody());
        assertEquals(2, serie.size());
        assertEquals("2026-10-01", serie.get(0).get("periodo").asText());
        assertEquals(100.0, serie.get(0).get("faturamento").asDouble(), 0.001);
        assertEquals(154.0, serie.get(1).get("faturamento").asDouble(), 0.001);
    }

    @Test
    void vendasPorHora_deveTer24PosicoesComOsPedidosNaHoraCerta() throws Exception {
        JsonNode horas = json.readTree(get("/vendas-por-hora?" + SEMANA).getBody());
        assertEquals(24, horas.size());
        assertEquals(1, horas.get(19).get("pedidos").asInt()); // P1 às 19:30
        assertEquals(1, horas.get(20).get("pedidos").asInt()); // P2 às 20:10
        assertEquals(1, horas.get(23).get("pedidos").asInt()); // P5 às 23:59
        assertEquals(0, horas.get(21).get("pedidos").asInt()); // P3 às 21:00 foi cancelado
    }

    @Test
    void vendasPorCategoria_deveOrdenarPorFaturamento() throws Exception {
        JsonNode cats = json.readTree(get("/vendas-por-categoria?" + SEMANA).getBody());
        assertEquals("Pizza", cats.get(0).get("categoria").asText());
        assertEquals(135.0, cats.get(0).get("faturamento").asDouble(), 0.001); // 90 + 45
        assertEquals("Bebida", cats.get(1).get("categoria").asText());
        assertEquals(19.0, cats.get(1).get("faturamento").asDouble(), 0.001);
    }

    // ===================== GET /status, /pedidos-recentes (RF03) =====================

    @Test
    void status_deveContarTodosOsStatusInclusiveCancelado() throws Exception {
        JsonNode lista = json.readTree(get("/status?" + SEMANA).getBody());
        assertEquals(7, lista.size());
        assertEquals("RECEBIDO", lista.get(0).get("status").asText());
        assertEquals(1, quantidade(lista, "EM_PREPARACAO"));
        assertEquals(2, quantidade(lista, "FINALIZADO"));
        assertEquals(1, quantidade(lista, "CANCELADO"));
    }

    @Test
    void pedidosRecentes_deveOrdenarDoMaisNovoEResolverNomeDoCliente() throws Exception {
        JsonNode lista = json.readTree(get("/pedidos-recentes?" + SEMANA + "&limite=10").getBody());
        assertEquals(4, lista.size());
        assertEquals("P5", lista.get(0).get("id").asText());
        assertEquals("Ana Souza", lista.get(0).get("cliente").asText());
        assertEquals("P2", lista.get(2).get("id").asText());
        assertEquals("Cliente não identificado", lista.get(2).get("cliente").asText());
        assertEquals("Cancelado", lista.get(1).get("descricaoStatus").asText());
        assertEquals("2x Pizza Calabresa, 1x Refrigerante 2L", lista.get(3).get("itens").asText());
    }

    // ===================== GET /produtos-mais-vendidos (RF04) =====================

    @Test
    void produtosMaisVendidos_deveRankearPorQuantidadeSemCancelados() throws Exception {
        JsonNode r = json.readTree(get("/produtos-mais-vendidos?" + SEMANA + "&limite=5").getBody());
        assertEquals(3, r.size());
        assertEquals("Pizza Calabresa", r.get(0).get("nome").asText());
        assertEquals(2, r.get(0).get("quantidade").asInt());        // os 5 do P3 cancelado não contam
        assertEquals("Refrigerante 2L", r.get(1).get("nome").asText()); // empate em 2, desempata pela receita
        assertEquals("Pizza Mussarela", r.get(2).get("nome").asText());
        assertEquals("Bebida", r.get(1).get("categoria").asText());
    }

    // ===================== Erros (RF07) =====================

    @Test
    void dataInicialMaiorQueFinal_deveRetornar400ComMensagem() throws Exception {
        ResponseEntity<String> r = get("/resumo?inicio=2026-11-07&fim=2026-11-01");
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("A data inicial não pode ser posterior à data final.", json.readTree(r.getBody()).get("erro").asText());
    }

    @Test
    void categoriaInexistenteNoBanco_deveRetornar400() throws Exception {
        ResponseEntity<String> r = get("/resumo?categoria=Sushi");
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("Categoria inválida: Sushi", json.readTree(r.getBody()).get("erro").asText());
    }

    @Test
    void limiteForaDaFaixa_deveRetornar400() {
        assertEquals(HttpStatus.BAD_REQUEST, get("/produtos-mais-vendidos?limite=51").getStatusCode());
    }

    // ===================== Segurança =====================

    @Test
    void semChave_deveRetornar401() {
        ResponseEntity<String> r = http.getForEntity("/api/dashboard/resumo", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode());
    }

    @Test
    void chaveErrada_deveRetornar401() {
        assertEquals(HttpStatus.UNAUTHORIZED, chamar(HttpMethod.GET, "/resumo", "chave-errada").getStatusCode());
    }

    @Test
    void chaveNaoDaAcessoAOutrasApis() {
        assertEquals(HttpStatus.UNAUTHORIZED, chamarUrl(HttpMethod.GET, "/api/pedidos", CHAVE).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, chamarUrl(HttpMethod.GET, "/api/usuarios", CHAVE).getStatusCode());
    }

    // ===================== Persistência =====================

    @Test
    void apiSomenteLeitura_postENegadoEBancoNaoMuda() {
        long antes = mongo.count(new org.springframework.data.mongodb.core.query.Query(), Pedido.class);
        assertEquals(HttpStatus.FORBIDDEN, chamar(HttpMethod.POST, "/resumo", CHAVE).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, chamar(HttpMethod.DELETE, "/resumo", CHAVE).getStatusCode());
        get("/resumo?" + SEMANA);
        assertEquals(antes, mongo.count(new org.springframework.data.mongodb.core.query.Query(), Pedido.class));
    }

    @Test
    void inicializacao_deveCriarIndiceDeDataNosPedidos() {
        boolean existe = mongo.indexOps("pedidos").getIndexInfo().stream()
                .anyMatch(i -> i.getName().equals("idx_dataCriacao"));
        assertTrue(existe, "índice usado pelo filtro de período");
    }

    // ===================== Auxiliares =====================

    private ResponseEntity<String> get(String caminho) {
        return chamar(HttpMethod.GET, caminho, CHAVE);
    }

    private ResponseEntity<String> chamar(HttpMethod metodo, String caminho, String chave) {
        return chamarUrl(metodo, "/api/dashboard" + caminho, chave);
    }

    private ResponseEntity<String> chamarUrl(HttpMethod metodo, String url, String chave) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-API-KEY", chave);
        return http.exchange(url, metodo, new HttpEntity<>(h), String.class);
    }

    private static int quantidade(JsonNode lista, String status) {
        for (JsonNode s : lista) {
            if (s.get("status").asText().equals(status)) return s.get("quantidade").asInt();
        }
        return -1;
    }

    private static Produto produto(String id, String nome, String tipo, double preco) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(preco);
        p.setAtivo(true);
        return p;
    }

    private static ItemPedido item(String produtoId, String nome, double preco, int qtd) {
        ItemPedido i = new ItemPedido();
        i.setProdutoId(produtoId);
        i.setNomeProduto(nome);
        i.setPrecoUnitario(preco);
        i.setQuantidade(qtd);
        return i;
    }

    private static Pedido pedido(String id, String clienteId, String status, LocalDateTime data, Double desconto,
            ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setClienteId(clienteId);
        p.setStatus(status);
        p.setDataCriacao(data);
        p.setDescontoPercentual(desconto);
        p.setItens(new ArrayList<>(List.of(itens)));
        p.recalcularTotais();
        return p;
    }
}
