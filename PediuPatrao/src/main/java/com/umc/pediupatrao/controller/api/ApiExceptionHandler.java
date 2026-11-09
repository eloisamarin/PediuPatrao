package com.umc.pediupatrao.controller.api;

import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Respostas de erro padronizadas da API, sempre no formato {"erro": "mensagem"}:
 *   400 — regra de negócio ou requisição inválida
 *   404 — registro não encontrado
 *   415 — tipo de conteúdo não suportado
 * 401/403 (não autenticado / sem permissão / CSRF) continuam com o Spring Security.
 * Detalhes técnicos (stack trace, nomes de classes) nunca são devolvidos ao cliente.
 */
@RestControllerAdvice(basePackageClasses = ApiExceptionHandler.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> naoEncontrado(RecursoNaoEncontradoException e) {
        return erro(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Map<String, String>> regraNegocio(RegraNegocioException e) {
        return erro(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class })
    public ResponseEntity<Map<String, String>> requisicaoInvalida(Exception e) {
        log.warn("Requisição inválida na API: {}", e.getMessage());
        return erro(HttpStatus.BAD_REQUEST, "Requisição inválida: verifique os parâmetros e o corpo enviados.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> tipoNaoSuportado(HttpMediaTypeNotSupportedException e) {
        return erro(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Envie o corpo como application/json.");
    }

    private static ResponseEntity<Map<String, String>> erro(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(Map.of("erro", mensagem));
    }
}
