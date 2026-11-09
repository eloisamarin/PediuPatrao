package com.umc.pediupatrao.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Chamada à API sem estar logado: responde HTTP 401 em JSON (as telas continuam redirecionando para o login).
 */
@Component
public class ApiNaoAutenticadoEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        SegurancaUtil.responderJson(response, HttpServletResponse.SC_UNAUTHORIZED, "Autenticação necessária.");
    }
}
