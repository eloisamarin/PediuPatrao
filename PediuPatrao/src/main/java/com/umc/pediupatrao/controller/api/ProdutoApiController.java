package com.umc.pediupatrao.controller.api;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.service.ProdutoService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API de produtos — todos consultam, GERENTE cadastra/altera/exclui.
 */
@RestController
@RequestMapping("/api/produtos")
public class ProdutoApiController {

    private final ProdutoService produtoService;

    public ProdutoApiController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public List<Produto> listar() {
        return produtoService.listarProdutos();
    }

    @PostMapping
    public ResponseEntity<Produto> criar(@RequestBody Produto produto) {
        produto.setId(null); // criação nunca sobrescreve um produto existente
        return ResponseEntity.status(HttpStatus.CREATED).body(produtoService.salvar(produto));
    }

    @PutMapping("/{id}")
    public Produto atualizar(@PathVariable String id, @RequestBody Produto produto) {
        produto.setId(id);
        return produtoService.salvar(produto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable String id) {
        produtoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
