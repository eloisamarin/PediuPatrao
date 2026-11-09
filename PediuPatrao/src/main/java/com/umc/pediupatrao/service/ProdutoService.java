package com.umc.pediupatrao.service;

import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ProdutoRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Produtos (cardápio): todos os perfis consultam; somente o GERENTE altera,
 * pois o preço do produto define o valor do pedido.
 */
@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;

    public ProdutoService(ProdutoRepository produtoRepository, AuditoriaService auditoriaService) {
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("isAuthenticated()")
    public List<Produto> listarProdutos() {
        return produtoRepository.findAll();
    }

    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        Produto anterior = produtoRepository.findById(id).orElse(new Produto());
        produtoRepository.deleteById(id);
        auditoriaService.registrarAlteracao("EXCLUIR_PRODUTO", "Produto", id, Alteracoes.nova()
                .sempre("nome", anterior.getNome(), null)
                .sempre("preco", anterior.getPreco(), null)
                .lista(), null, null);
    }

    // Salva um novo produto ou atualiza um existente
    @PreAuthorize("hasRole('GERENTE')")
    public Produto salvar(Produto produto) {
        if (produto.getNome() == null || produto.getNome().isBlank()) {
            throw new RegraNegocioException("Informe o nome do produto.");
        }
        if (produto.getPreco() == null || produto.getPreco() < 0) {
            throw new RegraNegocioException("Informe um preço válido.");
        }
        if (produto.getId() != null && produto.getId().isBlank()) {
            produto.setId(null);
        }
        if (produto.getId() != null && !produtoRepository.existsById(produto.getId())) {
            throw new RecursoNaoEncontradoException("Produto não encontrado para atualização.");
        }
        if (produto.getAtivo() == null) {
            produto.setAtivo(false);
        }
        boolean novo = produto.getId() == null;
        Produto anterior = novo ? new Produto() : produtoRepository.findById(produto.getId()).orElse(new Produto());
        Produto salvo = produtoRepository.save(produto);
        Alteracoes alteracoes = Alteracoes.nova()
                .se("nome", anterior.getNome(), salvo.getNome())
                .se("tipo", anterior.getTipo(), salvo.getTipo())
                .se("preco", anterior.getPreco(), salvo.getPreco())
                .se("ativo", anterior.getAtivo(), salvo.getAtivo());
        if (novo || !alteracoes.vazia()) {
            auditoriaService.registrarAlteracao(novo ? "CRIAR_PRODUTO" : "ATUALIZAR_PRODUTO", "Produto", salvo.getId(),
                    alteracoes.lista(), null, null);
        }
        return salvo;
    }
}
