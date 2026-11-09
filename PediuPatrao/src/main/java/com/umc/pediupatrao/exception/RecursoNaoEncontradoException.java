package com.umc.pediupatrao.exception;

/**
 * O registro pedido (pedido, cliente, usuário...) não existe. Na API vira HTTP 404.
 */
public class RecursoNaoEncontradoException extends RegraNegocioException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
