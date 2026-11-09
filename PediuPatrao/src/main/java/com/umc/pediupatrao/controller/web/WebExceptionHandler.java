package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.exception.RegraNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Erros das telas que não passaram pelo executar(): parâmetros ausentes ou inválidos
 * (ex.: desconto "abc") e regras de negócio. O usuário volta para a tela de onde veio com
 * uma mensagem, em vez de ver um erro técnico.
 *
 * Acesso negado NÃO é tratado aqui: continua com o Spring Security (HTTP 403 + auditoria).
 */
@ControllerAdvice(basePackageClasses = WebControllerSupport.class)
public class WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    @ExceptionHandler({ MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class })
    public String parametroInvalido(Exception e, HttpServletRequest request) {
        log.warn("Requisição inválida em {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return voltarComErro(request, "Dados inválidos ou incompletos. Confira o formulário e tente novamente.");
    }

    @ExceptionHandler(RegraNegocioException.class)
    public String regraNegocio(RegraNegocioException e, HttpServletRequest request) {
        return voltarComErro(request, e.getMessage());
    }

    private String voltarComErro(HttpServletRequest request, String mensagem) {
        RequestContextUtils.getOutputFlashMap(request).put(WebControllerSupport.ERRO, mensagem);
        return "redirect:" + caminhoSeguroDeRetorno(request);
    }

    // Volta para a página anterior somente se for deste próprio sistema (evita "open redirect")
    static String caminhoSeguroDeRetorno(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                boolean mesmoHost = uri.getHost() == null || uri.getHost().equalsIgnoreCase(request.getServerName());
                String caminho = uri.getRawPath();
                if (mesmoHost && caminho != null && caminho.startsWith("/") && !caminho.startsWith("//")) {
                    return caminho;
                }
            } catch (IllegalArgumentException ignorado) {
                // Referer malformado: usa a página inicial
            }
        }
        return "/";
    }
}
