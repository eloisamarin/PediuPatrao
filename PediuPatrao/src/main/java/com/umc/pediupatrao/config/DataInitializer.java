package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Usuários iniciais.
 *
 * - ADMIN inicial: criado SOMENTE se o sistema não tiver nenhum ADMIN (primeira execução).
 *   Assim, excluir/renomear o "admin" depois não faz ele voltar com a senha padrão ao reiniciar.
 * - GERENTE e ATENDENTE de demonstração: só quando pediupatrao.usuarios-demo=true (desenvolvimento).
 *
 * A senha padrão deve ser trocada na tela de Usuários logo após o primeiro acesso.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String SENHA_PADRAO = "teste123";

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, PasswordEncoder encoder,
            @Value("${pediupatrao.usuarios-demo:false}") boolean usuariosDemo) {
        return args -> {
            corrigirUsuariosSemPerfil(repo);
            if (repo.countByRole(Perfil.ADMIN.name()) == 0) {
                criarSeNaoExistir(repo, encoder, "admin", Perfil.ADMIN);
            }
            if (usuariosDemo) {
                criarSeNaoExistir(repo, encoder, "gerente", Perfil.GERENTE);
                criarSeNaoExistir(repo, encoder, "atendente", Perfil.ATENDENTE);
            }
        };
    }

    // Contas antigas criadas antes dos perfis (role nula ou "USER") com os nomes padrão recebem o perfil correspondente
    private void corrigirUsuariosSemPerfil(UsuarioRepository repo) {
        for (var padrao : new Object[][] { { "admin", Perfil.ADMIN }, { "gerente", Perfil.GERENTE }, { "atendente", Perfil.ATENDENTE } }) {
            repo.findByUsername((String) padrao[0]).ifPresent(u -> {
                if (Perfil.de(u.getRole()).isEmpty()) {
                    u.setRole(((Perfil) padrao[1]).name());
                    repo.save(u);
                    log.info("Perfil {} atribuído ao usuário {}", padrao[1], padrao[0]);
                }
            });
        }
    }

    private void criarSeNaoExistir(UsuarioRepository repo, PasswordEncoder encoder, String username, Perfil perfil) {
        if (repo.findByUsername(username).isPresent()) {
            return;
        }
        Usuario user = new Usuario();
        user.setUsername(username);
        user.setPassword(encoder.encode(SENHA_PADRAO));
        user.setRole(perfil.name());
        repo.save(user);
        log.warn("Usuário '{}' ({}) criado com a senha padrão. Troque a senha na tela de Usuários.", username, perfil);
    }
}
