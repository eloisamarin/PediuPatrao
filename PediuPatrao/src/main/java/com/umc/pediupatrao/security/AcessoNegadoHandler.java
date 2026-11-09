package com.umc.pediupatrao.security;

import com.umc.pediupatrao.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * Acesso negado (HTTP 403). Toda ocorrência é auditada, separando dois casos:
 *
 * - CSRF_INVALIDO: formulário/requisição sem token CSRF válido. Acontece num ataque CSRF ou quando a
 *   sessão expirou com a página aberta. Se não há mais sessão, o usuário é levado ao login com aviso.
 * - ACESSO_NEGADO: o perfil do usuário não tem permissão para a rota/ação.
 *
 * Na API a resposta é JSON; nas telas é a página de erro com a explicação.
 */
@Component
public class AcessoNegadoHandler implements AccessDeniedHandler {

    public static final String ATRIBUTO_MOTIVO = "motivoErro";

    private final AuditoriaService auditoriaService;

    public AcessoNegadoHandler(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean logado = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        boolean csrf = exception instanceof CsrfException;
        String rota = request.getMethod() + " " + SegurancaUtil.textoSeguro(request.getRequestURI());

        auditoriaService.registrar(auth, csrf ? "CSRF_INVALIDO" : "ACESSO_NEGADO", null, null, rota);

        if (SegurancaUtil.isApi(request)) {
            SegurancaUtil.responderJson(response, HttpServletResponse.SC_FORBIDDEN, csrf
                    ? "Token CSRF ausente ou inválido. Envie o cabeçalho X-CSRF-TOKEN."
                    : "Você não tem permissão para esta ação.");
            return;
        }
        if (csrf && !logado) {
            // Sessão expirou com a página aberta: pede para entrar de novo em vez de mostrar um erro
            response.sendRedirect(request.getContextPath() + "/login?expirada");
            return;
        }
        request.setAttribute(ATRIBUTO_MOTIVO, csrf
                ? "O formulário expirou ou não veio desta aplicação. Recarregue a página e tente novamente."
                : "Seu perfil não tem permissão para acessar este recurso.");
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
