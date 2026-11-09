package com.umc.pediupatrao.entity;

/**
 * Um campo modificado numa operação auditada: nome do campo, valor anterior e novo valor.
 * Em inclusões o valor anterior é nulo.
 */
public class CampoAlterado {
    private String campo;
    private String valorAnterior;
    private String valorNovo;

    public CampoAlterado() {
    }

    public CampoAlterado(String campo, String valorAnterior, String valorNovo) {
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
    }

    public String getCampo() { return campo; }
    public String getValorAnterior() { return valorAnterior; }
    public String getValorNovo() { return valorNovo; }
    // Sem setters: o registro não é alterado depois de criado (o MongoDB preenche os campos diretamente)
}
