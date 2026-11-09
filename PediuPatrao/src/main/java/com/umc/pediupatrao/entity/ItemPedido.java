package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Item de um pedido (fica dentro do documento do pedido no MongoDB).
 * Nome e preço são copiados do produto no momento do pedido, para o histórico
 * não mudar se o produto for alterado depois.
 */
public class ItemPedido {
    private String produtoId;
    private String nomeProduto;
    private Double precoUnitario;
    private int quantidade;
    private String sabor;      // sabor / observação do item
    private Double subtotal;

    public String getProdutoId() { return produtoId; }
    public void setProdutoId(String produtoId) { this.produtoId = produtoId; }

    public String getNomeProduto() { return nomeProduto; }
    public void setNomeProduto(String nomeProduto) { this.nomeProduto = nomeProduto; }

    public Double getPrecoUnitario() { return precoUnitario; }
    public void setPrecoUnitario(Double precoUnitario) { this.precoUnitario = precoUnitario; }

    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }

    public String getSabor() { return sabor; }
    public void setSabor(String sabor) { this.sabor = sabor; }

    public Double getSubtotal() { return subtotal; }
    public void setSubtotal(Double subtotal) { this.subtotal = subtotal; }

    // subtotal = preço unitário x quantidade, com 2 casas decimais
    public void calcularSubtotal() {
        BigDecimal preco = precoUnitario == null ? BigDecimal.ZERO : BigDecimal.valueOf(precoUnitario);
        this.subtotal = preco.multiply(BigDecimal.valueOf(quantidade)).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // Ex.: "2x Pizza Grande (Calabresa)"
    public String getDescricao() {
        return quantidade + "x " + nomeProduto + (sabor == null || sabor.isBlank() ? "" : " (" + sabor.trim() + ")");
    }
}
