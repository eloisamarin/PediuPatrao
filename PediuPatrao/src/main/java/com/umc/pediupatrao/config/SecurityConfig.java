package com.umc.pediupatrao.config;

import com.umc.pediupatrao.security.AcessoNegadoHandler;
import com.umc.pediupatrao.security.DashboardApiKeyFilter;
import com.umc.pediupatrao.security.ApiNaoAutenticadoEntryPoint;
import com.umc.pediupatrao.security.LoginFalhaHandler;
import com.umc.pediupatrao.security.LoginSucessoHandler;
import com.umc.pediupatrao.security.SegurancaUtil;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Autenticação e autorização por perfil.
 *
 * A proteção é feita em duas camadas no backend:
 *  1) aqui, por rota (URL + método HTTP);
 *  2) nos services, com @PreAuthorize (vale mesmo se uma rota nova for criada sem regra).
 * Ocultar botões nas telas é só conveniência visual.
 *
 * CSRF: ligado para TODAS as requisições que alteram dados (POST/PUT/DELETE), inclusive login,
 * logout e API. Formulários Thymeleaf com th:action recebem o token automaticamente; chamadas à API
 * devem enviar o cabeçalho X-CSRF-TOKEN (o valor está na meta tag "_csrf" do layout).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // habilita @PreAuthorize nos services
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String GERENTE = "GERENTE";
    private static final String ATENDENTE = "ATENDENTE";

    @Bean
    public AuthenticationProvider authenticationProvider(UsuarioDetailsService usuarioDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(usuarioDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider; // por padrão não revela se o usuário existe (usuário inexistente = senha errada)
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SessionRegistry sessionRegistry,
            LoginSucessoHandler loginSucesso, LoginFalhaHandler loginFalha,
            AcessoNegadoHandler acessoNegado, ApiNaoAutenticadoEntryPoint apiNaoAutenticado,
            @Value("${pediupatrao.dashboard.api-key:}") String chaveApiDashboard) throws Exception {
        http
                // ===== Chave de API (X-API-KEY) só para leitura do dashboard por ferramentas externas (Grafana) =====
                .addFilterBefore(new DashboardApiKeyFilter(chaveApiDashboard), UsernamePasswordAuthenticationFilter.class)

                // ===== CSRF (padrão do Spring: token por sessão, exigido em POST/PUT/PATCH/DELETE) =====
                .csrf(csrf -> { })

                // ===== Autorização por rota =====
                .authorizeHttpRequests(auth -> auth
                        // Público
                        .requestMatchers("/login", "/error", "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**").permitAll()
                        // "Esqueci a senha": telas públicas (quem esqueceu a senha não está logado). POST continua exigindo CSRF.
                        .requestMatchers("/esqueci-senha", "/redefinir-senha").permitAll()

                        // ADMIN: cadastro e gestão de usuários e perfis (tela e API)
                        .requestMatchers("/usuarios", "/usuarios/**", "/api/usuarios", "/api/usuarios/**").hasRole(ADMIN)
                        .requestMatchers("/admin").hasRole(ADMIN)

                        // Auditoria: somente consulta (GET), somente ADMIN e GERENTE.
                        // Qualquer tentativa de criar/alterar/excluir registros pela web ou API é negada.
                        .requestMatchers(HttpMethod.GET, "/auditoria", "/api/auditoria").hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers("/auditoria/**", "/api/auditoria/**").denyAll()

                        // Dashboard administrativo: somente consulta (GET). Tela: ADMIN e GERENTE.
                        // API: ADMIN, GERENTE e o usuário técnico do Grafana (chave X-API-KEY, perfil LEITOR_DASHBOARD)
                        .requestMatchers(HttpMethod.GET, "/dashboard").hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/**").hasAnyRole(ADMIN, GERENTE, DashboardApiKeyFilter.PERFIL)
                        .requestMatchers("/dashboard/**", "/api/dashboard/**").denyAll()

                        // Pedidos — ações exclusivas do GERENTE (nem o ADMIN pode)
                        .requestMatchers("/pedidos/cancelamentos", "/pedidos/descontos").hasRole(GERENTE)
                        .requestMatchers(HttpMethod.POST, "/pedidos/*/status", "/pedidos/*/desconto", "/pedidos/*/rejeitar-desconto",
                                "/pedidos/*/cancelar", "/pedidos/*/rejeitar-cancelamento").hasRole(GERENTE)
                        .requestMatchers("/api/pedidos/*/status", "/api/pedidos/*/desconto", "/api/pedidos/*/rejeitar-desconto",
                                "/api/pedidos/*/cancelar", "/api/pedidos/*/rejeitar-cancelamento").hasRole(GERENTE)

                        // Pedidos — ATENDENTE registra e solicita cancelamento
                        .requestMatchers("/pedidos/novo").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.POST, "/pedidos/salvar", "/pedidos/*/solicitar-cancelamento").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.POST, "/api/pedidos", "/api/pedidos/*/solicitar-cancelamento").hasRole(ATENDENTE)

                        // Pedidos — consulta (lista e detalhes) para os três perfis
                        .requestMatchers(HttpMethod.GET, "/pedidos", "/pedidos/*", "/api/pedidos", "/api/pedidos/*")
                                .hasAnyRole(ADMIN, GERENTE, ATENDENTE)

                        // Clientes — ATENDENTE cadastra/atualiza, GERENTE consulta
                        .requestMatchers("/clientes/novo", "/clientes/editar/**").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.POST, "/clientes/salvar").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.POST, "/api/clientes").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.PUT, "/api/clientes/*").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.GET, "/clientes", "/api/clientes").hasAnyRole(ATENDENTE, GERENTE)
                        .requestMatchers("/clientes/**", "/api/clientes/**").denyAll() // ex.: exclusão não é permitida a nenhum perfil

                        // Produtos — todos consultam, GERENTE altera
                        .requestMatchers(HttpMethod.GET, "/produtos", "/api/produtos").authenticated()
                        .requestMatchers("/produtos/**", "/api/produtos/**").hasRole(GERENTE)

                        // Qualquer outra rota exige login
                        .anyRequest().authenticated()
                )

                // ===== Login / logout =====
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(loginSucesso)   // zera tentativas e audita
                        .failureHandler(loginFalha)     // conta tentativas, bloqueia e audita
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")           // só POST com token CSRF (botão "Sair" no menu)
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )

                // ===== Sessão =====
                .sessionManagement(sessao -> sessao
                        .sessionFixation(fixacao -> fixacao.changeSessionId()) // novo ID de sessão a cada login
                        .maximumSessions(5)                                    // registra sessões no SessionRegistry
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl("/login?expirada")                         // sessão encerrada pelo ADMIN
                )

                // ===== Erros de segurança =====
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(acessoNegado)                     // 403 + auditoria (perfil ou CSRF)
                        .authenticationEntryPoint(naoAutenticado(apiNaoAutenticado)) // sem login: API → 401, telas → login
                )

                // ===== Cabeçalhos =====
                // Padrões do Spring já ativos: X-Frame-Options DENY, X-Content-Type-Options nosniff, Cache-Control no-store
                .headers(headers -> headers
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                );

        return http.build();
    }

    // Quem não está logado: chamadas à API recebem 401 em JSON; telas são redirecionadas para o login
    private static AuthenticationEntryPoint naoAutenticado(ApiNaoAutenticadoEntryPoint api) {
        LoginUrlAuthenticationEntryPoint telas = new LoginUrlAuthenticationEntryPoint("/login");
        return (request, response, excecao) -> {
            if (SegurancaUtil.isApi(request)) {
                api.commence(request, response, excecao);
            } else {
                telas.commence(request, response, excecao);
            }
        };
    }
}
