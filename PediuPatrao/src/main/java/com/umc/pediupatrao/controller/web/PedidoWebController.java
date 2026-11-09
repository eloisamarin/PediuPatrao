package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Telas de pedidos. Regras e permissões ficam no PedidoService (@PreAuthorize) e no SecurityConfig.
 */
@Controller
@RequestMapping("/pedidos")
public class PedidoWebController extends WebControllerSupport {

    private static final String LISTA = "/pedidos";
    private static final String ABA_CANCELAMENTOS = "/pedidos/cancelamentos";
    private static final String ABA_DESCONTOS = "/pedidos/descontos";

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;

    public PedidoWebController(PedidoService pedidoService, ClienteService clienteService, ProdutoService produtoService) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.produtoService = produtoService;
    }

    // ===== Consulta (ADMIN, GERENTE, ATENDENTE) =====

    @GetMapping
    public String listar(Model model) {
        List<Pedido> pedidos = pedidoService.listarPedidos();
        // Mais recentes primeiro (pedidos antigos sem data ficam no fim)
        pedidos.sort(Comparator.comparing(Pedido::getDataCriacao, Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
        model.addAttribute("pedidos", pedidos);
        model.addAttribute("nomesClientes", pedidoService.nomesDosClientes());
        return pagina(model, "pedidos :: content");
    }

    // Detalhes: itens, preços, desconto, total, responsável, histórico de status
    @GetMapping("/{id}")
    public String detalhes(@PathVariable String id, Model model, RedirectAttributes redirect) {
        try {
            Pedido pedido = pedidoService.buscarPorId(id);
            model.addAttribute("pedido", pedido);
            model.addAttribute("nomeCliente", pedidoService.nomesDosClientes().getOrDefault(pedido.getClienteId(), "—"));
            return pagina(model, "pedidos/detalhes :: content");
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute(ERRO, e.getMessage());
            return "redirect:" + LISTA;
        }
    }

    // ===== ATENDENTE =====

    @GetMapping("/novo")
    public String novo(Model model) {
        model.addAttribute("pedido", new NovoPedidoRequest());
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtoService.listarProdutos().stream()
                .filter(p -> !Boolean.FALSE.equals(p.getAtivo()) && p.getPreco() != null)
                .toList());
        return pagina(model, "pedidos/form :: content");
    }

    @PostMapping("/salvar")
    public String criar(@ModelAttribute("pedido") NovoPedidoRequest pedido, RedirectAttributes redirect) {
        try {
            Pedido salvo = pedidoService.criarPedido(pedido);
            redirect.addFlashAttribute(SUCESSO, salvo.isDescontoSolicitado()
                    ? "Pedido registrado! O desconto foi enviado para aprovação do gerente."
                    : "Pedido registrado com sucesso!");
            return "redirect:/pedidos/" + salvo.getId();
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute(ERRO, e.getMessage());
            return "redirect:/pedidos/novo";
        }
    }

    @PostMapping("/{id}/solicitar-cancelamento")
    public String solicitarCancelamento(@PathVariable String id, @RequestParam String motivo, RedirectAttributes redirect) {
        return executar(redirect, LISTA, "Cancelamento solicitado ao gerente.",
                () -> pedidoService.solicitarCancelamento(id, motivo));
    }

    // ===== GERENTE =====

    @GetMapping("/cancelamentos")
    public String abaCancelamentos(Model model) {
        model.addAttribute("pedidos", pedidoService.listarSolicitacoesCancelamento());
        model.addAttribute("nomesClientes", pedidoService.nomesDosClientes());
        return pagina(model, "pedidos/cancelamentos :: content");
    }

    @GetMapping("/descontos")
    public String abaDescontos(Model model) {
        model.addAttribute("pedidos", pedidoService.listarSolicitacoesDesconto());
        model.addAttribute("nomesClientes", pedidoService.nomesDosClientes());
        model.addAttribute("opcoesDesconto", PedidoService.OPCOES_DESCONTO);
        model.addAttribute("descontoMaximo", (int) PedidoService.DESCONTO_MAXIMO);
        return pagina(model, "pedidos/descontos :: content");
    }

    @PostMapping("/{id}/status")
    public String alterarStatus(@PathVariable String id, @RequestParam String status, RedirectAttributes redirect) {
        return executar(redirect, LISTA, "Status atualizado.", () -> pedidoService.atualizarStatus(id, status));
    }

    // Desconto direto ou atendendo a uma solicitação: percentual escolhido/digitado (até 20%) e motivo opcional
    @PostMapping("/{id}/desconto")
    public String aplicarDesconto(@PathVariable String id, @RequestParam Double percentual,
            @RequestParam(required = false) String motivo, @RequestParam(defaultValue = ABA_DESCONTOS) String voltar,
            RedirectAttributes redirect) {
        String destino = LISTA.equals(voltar) ? LISTA : ABA_DESCONTOS; // só telas internas conhecidas
        return executar(redirect, destino, "Desconto aplicado e valor do pedido atualizado.",
                () -> pedidoService.aplicarDesconto(id, percentual, motivo));
    }

    @PostMapping("/{id}/rejeitar-desconto")
    public String rejeitarDesconto(@PathVariable String id, RedirectAttributes redirect) {
        return executar(redirect, ABA_DESCONTOS, "Solicitação de desconto rejeitada; valor mantido.",
                () -> pedidoService.rejeitarDesconto(id));
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable String id, @RequestParam(required = false) String justificativa,
            @RequestParam(defaultValue = LISTA) String voltar, RedirectAttributes redirect) {
        // Só volta para telas internas conhecidas (evita redirecionamento para sites externos)
        String destino = ABA_CANCELAMENTOS.equals(voltar) ? ABA_CANCELAMENTOS : LISTA;
        return executar(redirect, destino, "Pedido cancelado.", () -> pedidoService.cancelar(id, justificativa));
    }

    @PostMapping("/{id}/rejeitar-cancelamento")
    public String rejeitarCancelamento(@PathVariable String id, RedirectAttributes redirect) {
        return executar(redirect, ABA_CANCELAMENTOS, "Solicitação de cancelamento rejeitada.",
                () -> pedidoService.rejeitarCancelamento(id));
    }
}
