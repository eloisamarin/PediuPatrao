package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Pedido;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface PedidoRepository extends MongoRepository<Pedido, String> {
    List<Pedido> findByClienteId(String clienteId);
    List<Pedido> findByCancelamentoSolicitadoTrueAndStatusNot(String status);
    List<Pedido> findByDescontoSolicitadoTrueAndStatusNot(String status);

    // Dashboard: pedidos criados no intervalo [inicio, fim) — início incluído, fim excluído.
    // O filtro de data é feito no banco (usa o índice idx_dataCriacao) para não carregar outros períodos.
    // "fields" é uma projeção: traz só os campos que os indicadores usam. O histórico de status e os dados
    // de auditoria ficam no banco, o que reduz bastante o volume transferido do Atlas.
    @Query(value = "{ 'dataCriacao': { $gte: ?0, $lt: ?1 } }",
            fields = "{ 'clienteId': 1, 'status': 1, 'itens': 1, 'produtoId': 1, 'tipoPizza': 1, 'quantidade': 1, 'sabor': 1,"
                    + " 'valorBruto': 1, 'descontoPercentual': 1, 'valorDesconto': 1, 'valorFinal': 1,"
                    + " 'dataCriacao': 1, 'dataSaida': 1 }")
    List<Pedido> buscarPorPeriodo(LocalDateTime inicio, LocalDateTime fimExclusivo);
}
