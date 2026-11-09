package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.service.ClienteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Telas de clientes: ATENDENTE cadastra/atualiza, GERENTE consulta.
 */
@Controller
@RequestMapping("/clientes")
public class ClienteWebController extends WebControllerSupport {

    private static final String LISTA = "/clientes";

    private final ClienteService clienteService;

    public ClienteWebController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("clientes", clienteService.listarClientes());
        return pagina(model, "clientes/lista :: content");
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        model.addAttribute("cliente", new Cliente());
        return pagina(model, "clientes/form :: content");
    }

    @PostMapping("/salvar")
    public String criar(@ModelAttribute Cliente cliente, RedirectAttributes redirect) {
        return executar(redirect, LISTA, "Cliente criado com sucesso!", () -> clienteService.cadastrar(cliente));
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable String id, Model model, RedirectAttributes redirect) {
        return clienteService.buscarPorId(id)
                .map(c -> {
                    model.addAttribute("cliente", c);
                    return pagina(model, "clientes/form :: content");
                })
                .orElseGet(() -> {
                    redirect.addFlashAttribute(ERRO, "Cliente não encontrado.");
                    return "redirect:" + LISTA;
                });
    }

    @PostMapping("/editar/{id}")
    public String atualizar(@PathVariable String id, @ModelAttribute Cliente cliente, RedirectAttributes redirect) {
        return executar(redirect, LISTA, "Cliente atualizado com sucesso!", () -> clienteService.atualizar(id, cliente));
    }
}
