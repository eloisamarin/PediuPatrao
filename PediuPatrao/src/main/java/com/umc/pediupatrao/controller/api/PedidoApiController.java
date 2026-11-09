package com.umc.pediupatrao.controller.api;

import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.PedidoService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API de pedidos. As permissões de cada ação estão no SecurityConfig e no PedidoService.
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoApiController {

    private final PedidoService pedidoService;

    public PedidoApiController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    // ===== Consulta (ADMIN, GERENTE, ATENDENTE) =====

    @GetMapping
    public List<Pedido> listar() {
        return pedidoService.listarPedidos();
    }

    @GetMapping("/{id}")
    public Pedido buscar(@PathVariable String id) {
        return pedidoService.buscarPorId(id);
    }

    // ===== ATENDENTE =====

    // O corpo aceita só cliente e itens (produto, quantidade, sabor); totais são calculados no servidor
    @PostMapping
    public ResponseEntity<Pedido> criar(@RequestBody NovoPedidoRequest pedido) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.criarPedido(pedido));
    }

    @PostMapping("/{id}/solicitar-cancelamento")
    public Pedido solicitarCancelamento(@PathVariable String id, @RequestParam String motivo) {
        return pedidoService.solicitarCancelamento(id, motivo);
    }

    // ===== GERENTE =====

    @PutMapping("/{id}/status")
    public Pedido alterarStatus(@PathVariable String id, @RequestParam String status) {
        return pedidoService.atualizarStatus(id, status);
    }

    // Percentual de 0,01 a 20; motivo opcional (vai para a auditoria)
    @PostMapping("/{id}/desconto")
    public Pedido aplicarDesconto(@PathVariable String id, @RequestParam Double percentual,
            @RequestParam(required = false) String motivo) {
        return pedidoService.aplicarDesconto(id, percentual, motivo);
    }

    @PostMapping("/{id}/rejeitar-desconto")
    public Pedido rejeitarDesconto(@PathVariable String id) {
        return pedidoService.rejeitarDesconto(id);
    }

    @PostMapping("/{id}/cancelar")
    public Pedido cancelar(@PathVariable String id, @RequestParam(required = false) String justificativa) {
        return pedidoService.cancelar(id, justificativa);
    }

    @PostMapping("/{id}/rejeitar-cancelamento")
    public Pedido rejeitarCancelamento(@PathVariable String id) {
        return pedidoService.rejeitarCancelamento(id);
    }
}
