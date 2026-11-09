package com.umc.pediupatrao.security;

import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.TentativasLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Login sem sucesso: conta a falha, sugere redefinir a senha a partir da 3ª, bloqueia na 5ª e audita.
 * A mensagem para o usuário é genérica ("usuário ou senha inválidos"): não revela se o usuário existe.
 * A senha digitada NUNCA é registrada.
 */
@Component
public class LoginFalhaHandler implements AuthenticationFailureHandler {

    private final AuditoriaService auditoriaService;
    private final TentativasLoginService tentativasLogin;

    public LoginFalhaHandler(AuditoriaService auditoriaService, TentativasLoginService tentativasLogin) {
        this.auditoriaService = auditoriaService;
        this.tentativasLogin = tentativasLogin;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        String username = SegurancaUtil.textoSeguro(request.getParameter("username"));

        if (exception instanceof LockedException) {
            auditoriaService.registrar(username, "-", "LOGIN_BLOQUEADO", "Usuario", null,
                    "Tentativa de login em conta temporariamente bloqueada");
            response.sendRedirect(request.getContextPath() + "/login?bloqueado");
            return;
        }

        boolean acabouDeBloquear = tentativasLogin.registrarFalha(username);
        auditoriaService.registrar(username, "-", "LOGIN_FALHOU", "Usuario", null, "Usuário ou senha inválidos");
        if (acabouDeBloquear) {
            auditoriaService.registrar(username, "-", "CONTA_BLOQUEADA", "Usuario", null,
                    TentativasLoginService.MAXIMO_FALHAS + " tentativas sem sucesso; bloqueio de "
                    + TentativasLoginService.TEMPO_BLOQUEIO.toMinutes() + " minutos");
            response.sendRedirect(request.getContextPath() + "/login?bloqueado");
            return;
        }
        // A partir da 3ª senha errada seguida, a tela de login oferece o "Esqueci a senha".
        // Vale para qualquer nome digitado (exista ou não), então não revela quais usuários existem.
        if (tentativasLogin.falhas(username) >= TentativasLoginService.FALHAS_PARA_SUGERIR_REDEFINICAO) {
            response.sendRedirect(request.getContextPath() + "/login?error&redefinir");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/login?error");
    }
}
