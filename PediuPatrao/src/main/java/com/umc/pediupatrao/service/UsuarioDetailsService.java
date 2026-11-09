package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Carrega o usuário para o login (Spring Security).
 * - Usuário sem perfil válido não entra.
 * - Usuário com muitas tentativas erradas recentes fica bloqueado: o Spring recusa o login
 *   ANTES de conferir a senha, então a conta não pode ser "testada" durante o bloqueio.
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final TentativasLoginService tentativasLogin;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository, TentativasLoginService tentativasLogin) {
        this.usuarioRepository = usuarioRepository;
        this.tentativasLogin = tentativasLogin;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return usuarioRepository.findByUsername(username)
                .map(usuario -> {
                    Perfil perfil = Perfil.de(usuario.getRole())
                            .orElseThrow(() -> new UsernameNotFoundException("Usuário sem perfil válido"));
                    return User.builder()
                            .username(usuario.getUsername())
                            .password(usuario.getPassword())
                            .roles(perfil.name()) // ROLE_ADMIN, ROLE_GERENTE ou ROLE_ATENDENTE
                            .accountLocked(tentativasLogin.estaBloqueado(usuario.getUsername()))
                            .build();
                })
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
