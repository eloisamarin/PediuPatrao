package com.umc.pediupatrao.exception;

/**
 * Violação de uma regra de negócio (ex.: transição de status inválida, desconto acima do limite).
 * A mensagem é pensada para o usuário final e pode ser exibida na tela ou devolvida pela API (HTTP 400).
 *
 * Use esta exceção (e não IllegalArgumentException) para que erros internos inesperados
 * nunca sejam confundidos com mensagens de negócio e mostrados ao usuário.
 */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
