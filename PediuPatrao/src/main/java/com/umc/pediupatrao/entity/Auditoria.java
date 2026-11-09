package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Registro de auditoria (RF05): quem fez, com qual perfil, quando, qual operação, em qual registro,
 * quais campos mudaram (valor anterior → novo) e a justificativa, quando exigida.
 *
 * O registro é imutável: não há setters públicos e o AuditoriaRepository só permite inserir e consultar.
 */
@Document(collection = "auditoria")
public class Auditoria {

    // Nomes legíveis das operações, para a tela de consulta
    private static final Map<String, String> OPERACOES = Map.ofEntries(
            Map.entry("CRIAR_PEDIDO", "INCLUSÃO DE PEDIDO"),
            Map.entry("ALTERAR_STATUS", "MUDANÇA DE STATUS"),
            Map.entry("REGISTRAR_SAIDA", "SAÍDA DO PEDIDO"),
            Map.entry("SOLICITAR_DESCONTO", "SOLICITAÇÃO DE DESCONTO"),
            Map.entry("APLICAR_DESCONTO", "DESCONTO"),
            Map.entry("REJEITAR_DESCONTO", "DESCONTO REJEITADO"),
            Map.entry("SOLICITAR_CANCELAMENTO", "SOLICITAÇÃO DE CANCELAMENTO"),
            Map.entry("CANCELAR_PEDIDO", "CANCELAMENTO DE PEDIDO"),
            Map.entry("REJEITAR_CANCELAMENTO", "CANCELAMENTO REJEITADO"),
            Map.entry("CRIAR_CLIENTE", "INCLUSÃO DE CLIENTE"),
            Map.entry("ATUALIZAR_CLIENTE", "ALTERAÇÃO DE CLIENTE"),
            Map.entry("CRIAR_PRODUTO", "INCLUSÃO DE PRODUTO"),
            Map.entry("ATUALIZAR_PRODUTO", "ALTERAÇÃO DE PRODUTO"),
            Map.entry("EXCLUIR_PRODUTO", "EXCLUSÃO DE PRODUTO"),
            Map.entry("CRIAR_USUARIO", "INCLUSÃO DE USUÁRIO"),
            Map.entry("ALTERAR_PERFIL", "ALTERAÇÃO DE PERFIL"),
            Map.entry("ALTERAR_SENHA", "REDEFINIÇÃO DE SENHA"),
            Map.entry("EXCLUIR_USUARIO", "EXCLUSÃO DE USUÁRIO"),
            Map.entry("LOGIN", "LOGIN"),
            Map.entry("LOGIN_FALHOU", "LOGIN SEM SUCESSO"),
            Map.entry("CONTA_BLOQUEADA", "CONTA BLOQUEADA (TENTATIVAS)"),
            Map.entry("LOGIN_BLOQUEADO", "LOGIN EM CONTA BLOQUEADA"),
            Map.entry("ACESSO_NEGADO", "ACESSO NEGADO"),
            Map.entry("CSRF_INVALIDO", "TOKEN CSRF INVÁLIDO"));

    @Id
    private String id;
    private LocalDateTime dataHora;
    private String usuario;
    private String perfil;
    private String acao;        // tipo de operação (código), ex.: ATUALIZAR_CLIENTE
    private String entidade;    // entidade afetada, ex.: Cliente, Pedido
    private String entidadeId;  // identificador do registro afetado
    private List<CampoAlterado> campos = new ArrayList<>(); // campos modificados: anterior → novo
    private String justificativa;
    private String detalhes;

    // Formato antigo (texto livre), mantido só para exibir registros gravados antes deste modelo
    private String dadosAnteriores;
    private String dadosPosteriores;

    protected Auditoria() {
        // usado pelo MongoDB ao ler o registro
    }

    public Auditoria(LocalDateTime dataHora, String usuario, String perfil, String acao, String entidade,
            String entidadeId, List<CampoAlterado> campos, String justificativa, String detalhes) {
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.perfil = perfil;
        this.acao = acao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.campos = campos == null ? new ArrayList<>() : new ArrayList<>(campos);
        this.justificativa = justificativa;
        this.detalhes = detalhes;
    }

    public String getId() { return id; }
    public LocalDateTime getDataHora() { return dataHora; }
    public String getUsuario() { return usuario; }
    public String getPerfil() { return perfil; }
    public String getAcao() { return acao; }
    public String getEntidade() { return entidade; }
    public String getEntidadeId() { return entidadeId; }
    public List<CampoAlterado> getCampos() { return campos == null ? List.of() : Collections.unmodifiableList(campos); }
    public String getJustificativa() { return justificativa; }
    public String getDetalhes() { return detalhes; }
    public String getDadosAnteriores() { return dadosAnteriores; }
    public String getDadosPosteriores() { return dadosPosteriores; }

    // Tentativas de acesso indevido (destacadas em vermelho na consulta)
    public boolean isEventoSeguranca() {
        return acao != null && Set.of("ACESSO_NEGADO", "CSRF_INVALIDO", "LOGIN_FALHOU", "CONTA_BLOQUEADA", "LOGIN_BLOQUEADO")
                .contains(acao);
    }

    // Nome legível da operação (ex.: "ALTERAÇÃO DE CLIENTE")
    public String getOperacao() {
        return acao == null ? "—" : OPERACOES.getOrDefault(acao, acao.replace('_', ' '));
    }
}
