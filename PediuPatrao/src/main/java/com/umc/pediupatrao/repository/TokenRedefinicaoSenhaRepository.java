package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.TokenRedefinicaoSenha;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TokenRedefinicaoSenhaRepository extends MongoRepository<TokenRedefinicaoSenha, String> {
    Optional<TokenRedefinicaoSenha> findByTokenHash(String tokenHash);
    List<TokenRedefinicaoSenha> findByUsuarioId(String usuarioId);
    void deleteByUsuarioId(String usuarioId);
}
