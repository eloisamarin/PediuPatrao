package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Auditoria;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Somente CONSULTA da auditoria. De propósito não estende MongoRepository/CrudRepository:
 * assim não existem save, delete ou update disponíveis para o restante do sistema.
 * A gravação é feita só pelo AuditoriaService, com insert (nunca sobrescreve um registro).
 */
public interface AuditoriaRepository extends Repository<Auditoria, String> {
    List<Auditoria> findTop500ByOrderByDataHoraDesc();
    List<Auditoria> findTop500ByEntidadeOrderByDataHoraDesc(String entidade);
    List<Auditoria> findByEntidadeAndEntidadeIdOrderByDataHoraDesc(String entidade, String entidadeId);
    long count();
}
