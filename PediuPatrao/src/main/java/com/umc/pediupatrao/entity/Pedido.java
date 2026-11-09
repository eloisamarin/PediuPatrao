package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pedidos")
public class Pedido {
    @Id
    private String id;
    private String clienteId;
    private String status;

    // Itens do pedido (um pedido pode ter vários produtos)
    private List<ItemPedido> itens = new ArrayList<>();

    // Campos de pedidos antigos (de um produto só) — mantidos para exibir o histórico
    private String produtoId;
    private String tipoPizza;
    private int quantidade;
    private String sabor;

    // Valores — sempre calculados no servidor por recalcularTotais(), nunca recebidos da requisição
    private Double valorBruto;          // soma dos subtotais dos itens
    private Double descontoPercentual;  // desconto autorizado pelo gerente (0 a 100)
    private Double valorDesconto;       // valorBruto x descontoPercentual
    private Double valorFinal;          // valorBruto - valorDesconto
    private String descontoAplicadoPor; // gerente que autorizou o desconto
    private LocalDateTime dataDesconto;

    // Solicitação de desconto feita pelo atendente ao registrar o pedido (aguarda o gerente)
    private boolean descontoSolicitado;
    private String motivoDesconto;
    private String descontoSolicitadoPor;
    private LocalDateTime dataSolicitacaoDesconto;

    // Registro: responsável e data/hora de entrada do pedido
    private String criadoPor;
    private LocalDateTime dataCriacao;

    // Saída: data/hora e responsável obrigatórios ao ir para SAIU_PARA_ENTREGA ou RETIRADO
    private LocalDateTime dataSaida;
    private String responsavelSaida;
    private String tipoSaida;           // SAIU_PARA_ENTREGA ou RETIRADO

    // Finalização
    private LocalDateTime dataFinalizacao;
    private String finalizadoPor;

    // Histórico de todas as transições de status (entrada, saída e intermediárias)
    private List<HistoricoStatus> historico = new ArrayList<>();

    // Cancelamento
    private boolean cancelamentoSolicitado;
    private String motivoCancelamento;        // motivo informado pelo atendente na solicitação
    private String cancelamentoSolicitadoPor;
    private LocalDateTime dataSolicitacaoCancelamento;
    private String canceladoPor;
    private LocalDateTime dataCancelamento;
    private String justificativaCancelamento; // justificativa obrigatória do gerente
    private String statusAntesDoCancelamento;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public List<ItemPedido> getItens() { return itens; }
    public void setItens(List<ItemPedido> itens) { this.itens = itens; }

    // Descrição dos itens para telas e auditoria. Pedidos antigos usam os campos de produto único.
    public List<String> getDescricaoItens() {
        if (itens != null && !itens.isEmpty()) {
            return itens.stream().map(ItemPedido::getDescricao).collect(Collectors.toList());
        }
        if (tipoPizza == null) {
            return List.of();
        }
        return List.of(quantidade + "x " + tipoPizza + (sabor == null || sabor.isBlank() ? "" : " (" + sabor + ")"));
    }

    public String getProdutoId() { return produtoId; }
    public void setProdutoId(String produtoId) { this.produtoId = produtoId; }

    public String getTipoPizza() { return tipoPizza; }
    public void setTipoPizza(String tipoPizza) { this.tipoPizza = tipoPizza; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }

    public String getSabor() { return sabor; }
    public void setSabor(String sabor) { this.sabor = sabor; }

    public Double getValorBruto() { return valorBruto; }
    public void setValorBruto(Double valorBruto) { this.valorBruto = valorBruto; }

    public Double getDescontoPercentual() { return descontoPercentual; }
    public void setDescontoPercentual(Double descontoPercentual) { this.descontoPercentual = descontoPercentual; }

    public Double getValorDesconto() { return valorDesconto; }
    public void setValorDesconto(Double valorDesconto) { this.valorDesconto = valorDesconto; }

    public Double getValorFinal() { return valorFinal; }
    public void setValorFinal(Double valorFinal) { this.valorFinal = valorFinal; }

    public String getDescontoAplicadoPor() { return descontoAplicadoPor; }
    public void setDescontoAplicadoPor(String descontoAplicadoPor) { this.descontoAplicadoPor = descontoAplicadoPor; }

    public LocalDateTime getDataDesconto() { return dataDesconto; }
    public void setDataDesconto(LocalDateTime dataDesconto) { this.dataDesconto = dataDesconto; }

    public boolean isDescontoSolicitado() { return descontoSolicitado; }
    public void setDescontoSolicitado(boolean descontoSolicitado) { this.descontoSolicitado = descontoSolicitado; }

    public String getMotivoDesconto() { return motivoDesconto; }
    public void setMotivoDesconto(String motivoDesconto) { this.motivoDesconto = motivoDesconto; }

    public String getDescontoSolicitadoPor() { return descontoSolicitadoPor; }
    public void setDescontoSolicitadoPor(String descontoSolicitadoPor) { this.descontoSolicitadoPor = descontoSolicitadoPor; }

    public LocalDateTime getDataSolicitacaoDesconto() { return dataSolicitacaoDesconto; }
    public void setDataSolicitacaoDesconto(LocalDateTime dataSolicitacaoDesconto) { this.dataSolicitacaoDesconto = dataSolicitacaoDesconto; }

    /**
     * Calcula os valores do pedido a partir dos itens e do desconto autorizado.
     * É o único lugar onde valorBruto, valorDesconto e valorFinal são definidos.
     * Usa BigDecimal com arredondamento comercial (HALF_UP) para evitar erros de centavos.
     */
    public void recalcularTotais() {
        BigDecimal bruto;
        if (itens != null && !itens.isEmpty()) {
            bruto = BigDecimal.ZERO;
            for (ItemPedido item : itens) {
                item.calcularSubtotal();
                bruto = bruto.add(BigDecimal.valueOf(item.getSubtotal()));
            }
        } else {
            // Pedidos antigos, sem lista de itens: mantém o valor que já estava gravado
            bruto = valorBruto == null ? BigDecimal.ZERO : BigDecimal.valueOf(valorBruto);
        }
        bruto = bruto.setScale(2, RoundingMode.HALF_UP);

        BigDecimal percentual = descontoPercentual == null ? BigDecimal.ZERO : BigDecimal.valueOf(descontoPercentual);
        BigDecimal desconto = bruto.multiply(percentual).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal finalCalculado = bruto.subtract(desconto).max(BigDecimal.ZERO);

        this.valorBruto = bruto.doubleValue();
        this.valorDesconto = desconto.doubleValue();
        this.valorFinal = finalCalculado.doubleValue();
    }

    public String getCriadoPor() { return criadoPor; }
    public void setCriadoPor(String criadoPor) { this.criadoPor = criadoPor; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public boolean isCancelamentoSolicitado() { return cancelamentoSolicitado; }
    public void setCancelamentoSolicitado(boolean cancelamentoSolicitado) { this.cancelamentoSolicitado = cancelamentoSolicitado; }

    public String getMotivoCancelamento() { return motivoCancelamento; }
    public void setMotivoCancelamento(String motivoCancelamento) { this.motivoCancelamento = motivoCancelamento; }

    public String getCancelamentoSolicitadoPor() { return cancelamentoSolicitadoPor; }
    public void setCancelamentoSolicitadoPor(String cancelamentoSolicitadoPor) { this.cancelamentoSolicitadoPor = cancelamentoSolicitadoPor; }

    public LocalDateTime getDataSolicitacaoCancelamento() { return dataSolicitacaoCancelamento; }
    public void setDataSolicitacaoCancelamento(LocalDateTime dataSolicitacaoCancelamento) { this.dataSolicitacaoCancelamento = dataSolicitacaoCancelamento; }

    public String getCanceladoPor() { return canceladoPor; }
    public void setCanceladoPor(String canceladoPor) { this.canceladoPor = canceladoPor; }

    public LocalDateTime getDataCancelamento() { return dataCancelamento; }
    public void setDataCancelamento(LocalDateTime dataCancelamento) { this.dataCancelamento = dataCancelamento; }

    public String getJustificativaCancelamento() { return justificativaCancelamento; }
    public void setJustificativaCancelamento(String justificativaCancelamento) { this.justificativaCancelamento = justificativaCancelamento; }

    public String getStatusAntesDoCancelamento() { return statusAntesDoCancelamento; }
    public void setStatusAntesDoCancelamento(String statusAntesDoCancelamento) { this.statusAntesDoCancelamento = statusAntesDoCancelamento; }

    public LocalDateTime getDataSaida() { return dataSaida; }
    public void setDataSaida(LocalDateTime dataSaida) { this.dataSaida = dataSaida; }

    public String getResponsavelSaida() { return responsavelSaida; }
    public void setResponsavelSaida(String responsavelSaida) { this.responsavelSaida = responsavelSaida; }

    public String getTipoSaida() { return tipoSaida; }
    public void setTipoSaida(String tipoSaida) { this.tipoSaida = tipoSaida; }

    public LocalDateTime getDataFinalizacao() { return dataFinalizacao; }
    public void setDataFinalizacao(LocalDateTime dataFinalizacao) { this.dataFinalizacao = dataFinalizacao; }

    public String getFinalizadoPor() { return finalizadoPor; }
    public void setFinalizadoPor(String finalizadoPor) { this.finalizadoPor = finalizadoPor; }

    public List<HistoricoStatus> getHistorico() { return historico; }
    public void setHistorico(List<HistoricoStatus> historico) { this.historico = historico; }

    public void registrarHistorico(String anterior, String novo, LocalDateTime dataHora, String usuario, String observacao) {
        if (historico == null) {
            historico = new ArrayList<>();
        }
        historico.add(new HistoricoStatus(anterior, novo, dataHora, usuario, observacao));
    }

    // Status atual como enum (aceita nomes antigos gravados no banco, ex.: EM_PREPARO, ENTREGUE)
    public StatusPedido getStatusAtual() {
        return StatusPedido.de(status).orElse(null);
    }

    // Próximos status permitidos (usado pela tela para mostrar só os botões válidos)
    public List<StatusPedido> getProximosStatus() {
        StatusPedido atual = getStatusAtual();
        return atual == null ? List.of() : atual.proximos().stream().toList();
    }

    // Antes da saída para entrega/retirada: ainda aceita cancelamento e desconto
    public boolean isAntesDaSaida() {
        StatusPedido atual = getStatusAtual();
        return atual != null && atual.isAntesDaSaida();
    }

    // FINALIZADO ou CANCELADO: não aceita mais nenhuma alteração
    public boolean isFinalizado() {
        StatusPedido atual = getStatusAtual();
        return atual != null && atual.isFinal();
    }
}
