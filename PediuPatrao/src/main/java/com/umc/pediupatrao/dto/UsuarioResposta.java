package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.Usuario;

/**
 * Usuário devolvido pela API: id, username, e-mail e perfil. O hash da senha nunca sai do servidor.
 */
public record UsuarioResposta(String id, String username, String email, String role) {

    public static UsuarioResposta de(Usuario usuario) {
        return new UsuarioResposta(usuario.getId(), usuario.getUsername(), usuario.getEmail(), usuario.getRole());
    }
}
