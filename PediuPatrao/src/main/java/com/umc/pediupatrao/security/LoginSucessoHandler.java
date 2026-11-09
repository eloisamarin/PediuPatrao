package com.umc.pediupatrao.security;

import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.TentativasLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Login bem-sucedido: zera o contador de tentativas, audita e vai para a página inicial.
 */
@Component
public class LoginSucessoHandler implements AuthenticationSuccessHandler {

    private final AuditoriaService auditoriaService;
    private final TentativasLoginService tentativasLogin;

    public LoginSucessoHandler(AuditoriaService auditoriaService, TentativasLoginService tentativasLogin) {
        this.auditoriaService = auditoriaService;
        this.tentativasLogin = tentativasLogin;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        tentativasLogin.limpar(authentication.getName());
        auditoriaService.registrar(authentication, "LOGIN", "Usuario", null, "Login realizado");
        response.sendRedirect(request.getContextPath() + "/");
    }
}
