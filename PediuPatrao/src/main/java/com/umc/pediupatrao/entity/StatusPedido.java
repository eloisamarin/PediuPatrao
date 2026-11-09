package com.umc.pediupatrao.entity;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Fluxo obrigatório do pedido:
 *
 *   RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA ┐
 *                                     → RETIRADO ──────────┴→ FINALIZADO
 *
 * CANCELADO é excepcional: só o GERENTE, e só antes da saída (RECEBIDO, EM_PREPARACAO ou PRONTO).
 * FINALIZADO e CANCELADO são estados finais: nenhuma alteração depois deles.
 */
public enum StatusPedido {
    RECEBIDO("Recebido"),
    EM_PREPARACAO("Em preparação"),
    PRONTO("Pronto"),
    SAIU_PARA_ENTREGA("Saiu para entrega"),
    RETIRADO("Retirado"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    // Próximos status permitidos a partir deste (sem contar o cancelamento, que tem operação própria)
    public Set<StatusPedido> proximos() {
        return switch (this) {
            case RECEBIDO -> EnumSet.of(EM_PREPARACAO);
            case EM_PREPARACAO -> EnumSet.of(PRONTO);
            case PRONTO -> EnumSet.of(SAIU_PARA_ENTREGA, RETIRADO);
            case SAIU_PARA_ENTREGA, RETIRADO -> EnumSet.of(FINALIZADO);
            case FINALIZADO, CANCELADO -> EnumSet.noneOf(StatusPedido.class);
        };
    }

    public boolean podeIrPara(StatusPedido destino) {
        return proximos().contains(destino);
    }

    // Saída do pedido: entregador saiu ou cliente retirou (exige registro de data/hora e responsável)
    public boolean isSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }

    // Antes da saída: ainda pode ser cancelado ou receber desconto
    public boolean isAntesDaSaida() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }

    public boolean isFinal() {
        return this == FINALIZADO || this == CANCELADO;
    }

    /**
     * Converte o texto gravado no banco. Aceita os nomes antigos usados antes deste fluxo
     * (EM_PREPARO → EM_PREPARACAO, ENTREGUE → FINALIZADO) para não quebrar pedidos já existentes.
     */
    public static Optional<StatusPedido> de(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        return switch (valor.trim().toUpperCase()) {
            case "EM_PREPARO" -> Optional.of(EM_PREPARACAO);
            case "ENTREGUE" -> Optional.of(FINALIZADO);
            default -> {
                try {
                    yield Optional.of(valueOf(valor.trim().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    yield Optional.empty();
                }
            }
        };
    }
}
