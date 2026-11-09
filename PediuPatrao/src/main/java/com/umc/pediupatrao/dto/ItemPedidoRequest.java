package com.umc.pediupatrao.dto;

/**
 * Item informado na requisição: apenas produto, quantidade e observação.
 * O preço unitário e o subtotal vêm do cadastro do produto, no servidor.
 */
public class ItemPedidoRequest {

    private String produtoId;
    private int quantidade;
    private String sabor;

    public String getProdutoId() { return produtoId; }
    public void setProdutoId(String produtoId) { this.produtoId = produtoId; }

    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }

    public String getSabor() { return sabor; }
    public void setSabor(String sabor) { this.sabor = sabor; }
}
