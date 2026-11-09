package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.service.ProdutoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Tela de produtos: todos consultam, GERENTE cadastra/altera/exclui.
 */
@Controller
@RequestMapping("/produtos")
public class ProdutoWebController extends WebControllerSupport {

    private static final String LISTA = "/produtos";

    private final ProdutoService produtoService;

    public ProdutoWebController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("produtos", produtoService.listarProdutos());
        model.addAttribute("produto", new Produto());
        return pagina(model, "produtos/lista :: content");
    }

    @PostMapping("/salvar")
    public String salvar(@ModelAttribute Produto produto, RedirectAttributes redirect) {
        return executar(redirect, LISTA, "Produto salvo com sucesso!", () -> produtoService.salvar(produto));
    }

    @PostMapping("/excluir/{id}")
    public String excluir(@PathVariable String id, RedirectAttributes redirect) {
        return executar(redirect, LISTA, "Produto excluído.", () -> produtoService.excluir(id));
    }
}
