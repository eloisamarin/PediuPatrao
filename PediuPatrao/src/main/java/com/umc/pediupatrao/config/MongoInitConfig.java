package com.umc.pediupatrao.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@Configuration
public class MongoInitConfig implements CommandLineRunner {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) throws Exception {
        if (!mongoTemplate.collectionExists("pedidos")) {
            mongoTemplate.createCollection("pedidos");
        }
        if (!mongoTemplate.collectionExists("clientes")) {
            mongoTemplate.createCollection("clientes");
        }
        if (!mongoTemplate.collectionExists("usuarios")) {
            mongoTemplate.createCollection("usuarios");
        }
        if (!mongoTemplate.collectionExists("produtos")) {
            mongoTemplate.createCollection("produtos");
        }
        if (!mongoTemplate.collectionExists("horariosAtendimento")) {
            mongoTemplate.createCollection("horariosAtendimento");
        }
        // Índice por data de criação: o dashboard consulta pedidos por período.
        // Sem ele, o MongoDB precisaria ler a coleção inteira a cada consulta. (Se já existir, não faz nada.)
        // Links de redefinição de senha: o MongoDB apaga sozinho os vencidos (índice TTL em expiraEm)
        mongoTemplate.indexOps("tokensRedefinicaoSenha").ensureIndex(
                new Index().on("expiraEm", Sort.Direction.ASC).expire(java.time.Duration.ZERO).named("idx_ttl_expiraEm"));
        mongoTemplate.indexOps("tokensRedefinicaoSenha").ensureIndex(
                new Index().on("tokenHash", Sort.Direction.ASC).unique().named("idx_tokenHash"));
        mongoTemplate.indexOps("pedidos").ensureIndex(new Index().on("dataCriacao", Sort.Direction.ASC).named("idx_dataCriacao"));
    }
}
