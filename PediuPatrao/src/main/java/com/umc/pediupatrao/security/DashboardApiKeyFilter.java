package com.umc.pediupatrao.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 - Permite que o Grafana leiam a API do Dashboard

  Como funciona:
   - a ferramenta envia o cabeçalho  X-API-KEY: <chave>;
   - se a chave for igual à configurada em pediupatrao.dashboard.api-key, a requisição é autenticada
     como o usuário técnico "grafana", com o perfil LEITOR_DASHBOARD;
   - esse perfil só tem acesso a GET /api/dashboard/** (regra no SecurityConfig e @PreAuthorize no service).

  Segurança:
   - vale SOMENTE para GET em /api/dashboard/** (qualquer outra rota ignora o cabeçalho);
   - nada é gravado em sessão: a chave precisa vir em toda requisição;
   - se a chave não estiver configurada (vazia), o recurso fica desligado;
   - a comparação é feita em tempo constante (MessageDigest.isEqual) para não vazar a chave por timing.
 */
public class DashboardApiKeyFilter extends OncePerRequestFilter {

    public static final String CABECALHO = "X-API-KEY";
    public static final String PERFIL = "LEITOR_DASHBOARD";

    private final byte[] chave;

    public DashboardApiKeyFilter(String chaveConfigurada) {
        this.chave = chaveConfigurada == null || chaveConfigurada.isBlank()
                ? null : chaveConfigurada.trim().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        return chave == null || !"GET".equals(request.getMethod()) || !caminho.startsWith("/api/dashboard/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recebida = request.getHeader(CABECALHO);
        if (recebida != null && MessageDigest.isEqual(chave, recebida.trim().getBytes(StandardCharsets.UTF_8))) {
            var auth = new UsernamePasswordAuthenticationToken("grafana", null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + PERFIL)));
            var contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(auth);
            SecurityContextHolder.setContext(contexto);
        }
        // Chave ausente ou errada: segue sem autenticar (o Spring Security responde 401)
        chain.doFilter(request, response);
    }
}
