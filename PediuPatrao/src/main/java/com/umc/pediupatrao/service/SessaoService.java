package com.umc.pediupatrao.service;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Encerra as sessões abertas de um usuário (ex.: perfil alterado, senha redefinida ou conta excluída).
 * Na próxima requisição a pessoa é mandada para o login e entra já com as permissões novas.
 */
@Service
public class SessaoService {

    private final SessionRegistry sessionRegistry;

    public SessaoService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public int encerrarSessoesDe(String username) {
        int encerradas = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            String nome = principal instanceof UserDetails u ? u.getUsername() : String.valueOf(principal);
            if (nome.equals(username)) {
                for (SessionInformation sessao : sessionRegistry.getAllSessions(principal, false)) {
                    sessao.expireNow();
                    encerradas++;
                }
            }
        }
        return encerradas;
    }
}
