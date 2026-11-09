package com.umc.pediupatrao.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Dados aceitos para registrar um pedido (formulário e API REST).
 *
 * Só existem aqui os campos que o atendente pode informar. Valores, descontos, total,
 * status, responsável e data/hora NÃO fazem parte desta classe: mesmo que a requisição
 * envie esses campos, eles são descartados e calculados no servidor.
 */
public class NovoPedidoRequest {

    private String clienteId;
    private List<ItemPedidoRequest> itens = new ArrayList<>();

    // O atendente só pode PEDIR desconto; o percentual é escolhido pelo gerente ao aprovar
    private boolean solicitarDesconto;
    private String motivoDesconto;

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public boolean isSolicitarDesconto() { return solicitarDesconto; }
    public void setSolicitarDesconto(boolean solicitarDesconto) { this.solicitarDesconto = solicitarDesconto; }

    public String getMotivoDesconto() { return motivoDesconto; }
    public void setMotivoDesconto(String motivoDesconto) { this.motivoDesconto = motivoDesconto; }

    public List<ItemPedidoRequest> getItens() { return itens; }
    public void setItens(List<ItemPedidoRequest> itens) { this.itens = itens; }
}
