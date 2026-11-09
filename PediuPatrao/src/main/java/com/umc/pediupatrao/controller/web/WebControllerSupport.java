package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.exception.RegraNegocioException;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Base das telas (Thymeleaf). Padroniza:
 * - a montagem da página dentro do layout;
 * - a execução de ações com mensagem de sucesso/erro (padrão POST → redirect → GET).
 *
 * Só erros de regra de negócio (RegraNegocioException) viram mensagem na tela.
 * Acesso negado sobe para o Spring Security (HTTP 403 + auditoria) e erros inesperados
 * vão para a página de erro genérica, sem expor detalhes técnicos.
 */
public abstract class WebControllerSupport {

    protected static final String LAYOUT = "layout";
    protected static final String SUCESSO = "sucesso";
    protected static final String ERRO = "erro";

    // Renderiza um fragmento (ex.: "pedidos :: content") dentro do layout com menu
    protected String pagina(Model model, String fragmento) {
        model.addAttribute("content", fragmento);
        return LAYOUT;
    }

    // Executa a ação e redireciona com a mensagem de sucesso ou com o erro de negócio
    protected String executar(RedirectAttributes redirect, String destino, String mensagemSucesso, Runnable acao) {
        try {
            acao.run();
            redirect.addFlashAttribute(SUCESSO, mensagemSucesso);
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute(ERRO, e.getMessage());
        }
        return "redirect:" + destino;
    }
}
