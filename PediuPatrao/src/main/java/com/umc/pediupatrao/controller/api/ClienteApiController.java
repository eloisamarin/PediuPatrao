package com.umc.pediupatrao.controller.api;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.service.ClienteService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API de clientes — ATENDENTE cadastra/atualiza, GERENTE consulta. Não há exclusão.
 */
@RestController
@RequestMapping("/api/clientes")
public class ClienteApiController {

    private final ClienteService clienteService;

    public ClienteApiController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public List<Cliente> listar() {
        return clienteService.listarClientes();
    }

    @PostMapping
    public ResponseEntity<Cliente> criar(@RequestBody Cliente cliente) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.cadastrar(cliente));
    }

    @PutMapping("/{id}")
    public Cliente atualizar(@PathVariable String id, @RequestBody Cliente cliente) {
        return clienteService.atualizar(id, cliente);
    }
}
