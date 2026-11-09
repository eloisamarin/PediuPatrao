package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;

/**
 * Uma transição de status do pedido: de onde veio, para onde foi, quando e quem fez.
 * Fica gravada dentro do pedido e nunca é apagada (histórico completo de entrada, saída e transições).
 */
public class HistoricoStatus {
    private String statusAnterior; // null na entrada (RECEBIDO)
    private String statusNovo;
    private LocalDateTime dataHora;
    private String usuario;
    private String observacao;     // ex.: justificativa do cancelamento

    public HistoricoStatus() {
    }

    public HistoricoStatus(String statusAnterior, String statusNovo, LocalDateTime dataHora, String usuario, String observacao) {
        this.statusAnterior = statusAnterior;
        this.statusNovo = statusNovo;
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.observacao = observacao;
    }

    public String getStatusAnterior() { return statusAnterior; }
    public void setStatusAnterior(String statusAnterior) { this.statusAnterior = statusAnterior; }

    public String getStatusNovo() { return statusNovo; }
    public void setStatusNovo(String statusNovo) { this.statusNovo = statusNovo; }

    public LocalDateTime getDataHora() { return dataHora; }
    public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
}
