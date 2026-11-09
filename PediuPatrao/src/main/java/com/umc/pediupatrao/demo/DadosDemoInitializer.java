package com.umc.pediupatrao.demo;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * Carrega (ou remove) os dados de demonstração no MongoDB ao iniciar a aplicação.
 *
 * Desligado por padrão. Para carregar 8 meses de pedidos, inicie a aplicação UMA vez com:
 *     DADOS_DEMO=gerar
 * Para apagar tudo o que foi gerado (somente IDs que começam com "demo-"):
 *     DADOS_DEMO=remover
 *
 * Proteções:
 *  - se já existirem pedidos "demo-", não gera de novo (não duplica);
 *  - a remoção apaga apenas registros com ID "demo-"; pedidos, clientes e produtos reais não são tocados;
 *  - grava em lotes de 1000 para não estourar a memória nem o limite do Atlas.
 */
@Component
@Order(100) // depois do DataInitializer (usuários) e do MongoInitConfig (coleções)
public class DadosDemoInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DadosDemoInitializer.class);
    private static final Criteria SO_DEMO = Criteria.where("_id").regex("^" + GeradorDadosDemo.PREFIXO);
    private static final int LOTE = 1000;

    private final MongoTemplate mongo;
    private final String acao;
    private final int meses;
    private final int pedidosPorDia;

    public DadosDemoInitializer(MongoTemplate mongo,
            @Value("${pediupatrao.dados-demo.acao:}") String acao,
            @Value("${pediupatrao.dados-demo.meses:8}") int meses,
            @Value("${pediupatrao.dados-demo.pedidos-por-dia:50}") int pedidosPorDia) {
        this.mongo = mongo;
        this.acao = acao == null ? "" : acao.trim().toLowerCase();
        this.meses = meses;
        this.pedidosPorDia = pedidosPorDia;
    }

    @Override
    public void run(String... args) {
        switch (acao) {
            case "gerar" -> gerar();
            case "remover" -> remover();
            case "" -> { }
            default -> log.warn("pediupatrao.dados-demo.acao='{}' inválido. Use 'gerar' ou 'remover'.", acao);
        }
    }

    private void gerar() {
        if (mongo.exists(new Query(SO_DEMO), Pedido.class)) {
            log.info("Dados de demonstração já existem. Nada foi gerado (use DADOS_DEMO=remover para apagar).");
            return;
        }
        long inicio = System.currentTimeMillis();

        // Reaproveita produtos já cadastrados com o mesmo nome (ex.: "Coca-Cola 2L") para não duplicar
        Map<String, Produto> existentes = new HashMap<>();
        mongo.findAll(Produto.class).forEach(p -> {
            if (p.getNome() != null) existentes.put(p.getNome().trim().toLowerCase(), p);
        });

        GeradorDadosDemo.Dados dados = new GeradorDadosDemo(2026L, LocalDateTime.now(), pedidosPorDia)
                .gerar(meses, existentes);

        inserir(dados.produtos(), Produto.class);
        inserir(dados.clientes(), Cliente.class);
        inserir(dados.pedidos(), Pedido.class);
        log.warn("Dados de demonstração gerados: {} pedidos, {} clientes, {} produtos novos ({} meses) em {} s. "
                + "Remova DADOS_DEMO=gerar antes da próxima execução.",
                dados.pedidos().size(), dados.clientes().size(), dados.produtos().size(), meses,
                (System.currentTimeMillis() - inicio) / 1000);
    }

    private <T> void inserir(List<T> lista, Class<T> tipo) {
        for (int i = 0; i < lista.size(); i += LOTE) {
            mongo.insert(lista.subList(i, Math.min(i + LOTE, lista.size())), tipo);
        }
    }

    private void remover() {
        long pedidos = mongo.remove(new Query(SO_DEMO), Pedido.class).getDeletedCount();
        long clientes = mongo.remove(new Query(SO_DEMO), Cliente.class).getDeletedCount();
        long produtos = mongo.remove(new Query(SO_DEMO), Produto.class).getDeletedCount();
        log.warn("Dados de demonstração removidos: {} pedidos, {} clientes, {} produtos.", pedidos, clientes, produtos);
    }
}
