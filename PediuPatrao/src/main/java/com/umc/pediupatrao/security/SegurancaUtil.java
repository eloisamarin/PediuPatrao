package com.umc.pediupatrao.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Funções auxiliares da camada de segurança.
 */
public final class SegurancaUtil {

    private static final int TAMANHO_MAXIMO = 100;

    private SegurancaUtil() {
    }

    public static boolean isApi(HttpServletRequest request) {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        return caminho.startsWith("/api/");
    }

    /**
     * Texto vindo da requisição (ex.: username digitado no login) pronto para ir para a auditoria/log:
     * sem quebras de linha ou caracteres de controle (evita "log injection") e com tamanho limitado.
     */
    public static String textoSeguro(String valor) {
        if (valor == null) {
            return "";
        }
        String limpo = valor.replaceAll("\\p{Cntrl}", "").trim();
        return limpo.length() > TAMANHO_MAXIMO ? limpo.substring(0, TAMANHO_MAXIMO) + "…" : limpo;
    }

    // Resposta de erro da API no mesmo formato do ApiExceptionHandler: {"erro": "..."}
    public static void responderJson(HttpServletResponse response, int status, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"erro\":\"" + mensagem.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
    }
}
