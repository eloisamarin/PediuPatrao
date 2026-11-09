package com.umc.pediupatrao.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Perfis de acesso do sistema. Somente estes valores são aceitos como "role" de um usuário.
 */
public enum Perfil {
    ADMIN("Administrador"),
    GERENTE("Gerente"),
    ATENDENTE("Atendente");

    private final String descricao;

    Perfil(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    // Converte o texto salvo no banco em um perfil válido (ignora valores desconhecidos, como o antigo "USER")
    public static Optional<Perfil> de(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(p -> p.name().equalsIgnoreCase(valor.trim()))
                .findFirst();
    }
}
