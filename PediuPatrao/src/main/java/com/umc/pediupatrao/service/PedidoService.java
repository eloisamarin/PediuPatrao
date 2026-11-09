package com.umc.pediupatrao.service;

import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Regras de pedidos. As permissões ficam aqui (e também nas rotas do SecurityConfig),
 * então valem tanto para as telas quanto para a API REST.
 *
 * Fluxo obrigatório: RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA | RETIRADO → FINALIZADO
 * (ver StatusPedido). CANCELADO é excepcional: só o GERENTE, com justificativa, antes da saída.
 *
 * - ATENDENTE: registra pedidos (podendo solicitar desconto) e solicita cancelamento
 * - GERENTE:   avança o status, aprova/rejeita desconto (até 20%) e cancela — exclusivo, nem o ADMIN pode
 * - ADMIN:     apenas consulta
 */
@Service
public class PedidoService {

    // Desconto percentual limitado a 20% do valor do pedido
    public static final double DESCONTO_MAXIMO = 20.0;
    // Atalhos oferecidos na tela do gerente (qualquer valor de 0,01% a 20% é aceito pela API)
    public static final List<Integer> OPCOES_DESCONTO = List.of(5, 10, 15, 20);
    private static final int MAXIMO_ITENS = 30;
    private static final int JUSTIFICATIVA_MINIMA = 5;

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
            ProdutoRepository produtoRepository, AuditoriaService auditoriaService) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
    }

    // ========================
    // CONSULTA
    // ========================
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }

    // Nomes dos clientes para exibir na lista de pedidos (o ADMIN vê pedidos, mas não tem acesso ao cadastro de clientes)
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public Map<String, String> nomesDosClientes() {
        return clienteRepository.findAll().stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(Cliente::getId, c -> c.getNome() == null ? "—" : c.getNome(), (a, b) -> a));
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public Pedido buscarPorId(String id) {
        return buscar(id);
    }

    // Solicitações de cancelamento ainda possíveis (pedido antes da saída)
    @PreAuthorize("hasRole('GERENTE')")
    public List<Pedido> listarSolicitacoesCancelamento() {
        return pedidoRepository.findByCancelamentoSolicitadoTrueAndStatusNot(StatusPedido.CANCELADO.name()).stream()
                .filter(Pedido::isAntesDaSaida)
                .toList();
    }

    // Solicitações de desconto ainda possíveis (pedido antes da saída)
    @PreAuthorize("hasRole('GERENTE')")
    public List<Pedido> listarSolicitacoesDesconto() {
        return pedidoRepository.findByDescontoSolicitadoTrueAndStatusNot(StatusPedido.CANCELADO.name()).stream()
                .filter(Pedido::isAntesDaSaida)
                .toList();
    }

    // ========================
    // ATENDENTE
    // ========================

    // Entrada do pedido: status RECEBIDO com data/hora de entrada e usuário responsável
    @PreAuthorize("hasRole('ATENDENTE')")
    public Pedido criarPedido(NovoPedidoRequest dados) {
        if (dados == null || dados.getClienteId() == null || !clienteRepository.existsById(dados.getClienteId())) {
            throw new RegraNegocioException("Selecione um cliente válido.");
        }

        // Linhas sem produto selecionado (deixadas em branco no formulário) são ignoradas
        List<ItemPedidoRequest> recebidos = dados.getItens() == null ? List.of() : dados.getItens().stream()
                .filter(i -> i != null && i.getProdutoId() != null && !i.getProdutoId().isBlank())
                .toList();
        if (recebidos.isEmpty()) {
            throw new RegraNegocioException("Adicione pelo menos um produto ao pedido.");
        }
        if (recebidos.size() > MAXIMO_ITENS) {
            throw new RegraNegocioException("O pedido pode ter no máximo " + MAXIMO_ITENS + " itens.");
        }

        // Cada item é montado no servidor: nome e preço vêm do cadastro do produto, não do formulário
        List<ItemPedido> itens = new ArrayList<>();
        for (ItemPedidoRequest recebido : recebidos) {
            Produto produto = produtoRepository.findById(recebido.getProdutoId()).orElse(null);
            if (produto == null || Boolean.FALSE.equals(produto.getAtivo()) || produto.getPreco() == null) {
                throw new RegraNegocioException("Um dos produtos selecionados não está ativo ou não tem preço.");
            }
            if (recebido.getQuantidade() < 1 || recebido.getQuantidade() > 100) {
                throw new RegraNegocioException("A quantidade de \"" + produto.getNome() + "\" deve estar entre 1 e 100.");
            }
            ItemPedido item = new ItemPedido();
            item.setProdutoId(produto.getId());
            item.setNomeProduto(produto.getNome());
            item.setPrecoUnitario(produto.getPreco());
            item.setQuantidade(recebido.getQuantidade());
            item.setSabor(recebido.getSabor() == null || recebido.getSabor().isBlank() ? null : recebido.getSabor().trim());
            itens.add(item);
        }

        // Monta um pedido novo: valores, desconto, status, responsável e data/hora são definidos aqui
        LocalDateTime agora = LocalDateTime.now();
        String usuario = usuarioLogado();
        Pedido pedido = new Pedido();
        pedido.setClienteId(dados.getClienteId());
        pedido.setItens(itens);
        pedido.setDescontoPercentual(0.0);
        pedido.recalcularTotais(); // subtotais, valor bruto e valor final calculados a partir dos itens
        pedido.setStatus(StatusPedido.RECEBIDO.name());
        pedido.setCriadoPor(usuario);
        pedido.setDataCriacao(agora);
        pedido.registrarHistorico(null, StatusPedido.RECEBIDO.name(), agora, usuario, "Entrada do pedido");

        // Desconto: o atendente só registra a solicitação; o valor NÃO muda até o gerente aprovar
        if (dados.isSolicitarDesconto()) {
            pedido.setDescontoSolicitado(true);
            pedido.setMotivoDesconto(dados.getMotivoDesconto() == null || dados.getMotivoDesconto().isBlank()
                    ? null : dados.getMotivoDesconto().trim());
            pedido.setDescontoSolicitadoPor(usuario);
            pedido.setDataSolicitacaoDesconto(agora);
        }
        pedidoRepository.save(pedido);

        // Inclusão: todos os campos do pedido com valor anterior vazio
        auditoriaService.registrarAlteracao("CRIAR_PEDIDO", "Pedido", pedido.getId(), Alteracoes.nova()
                .sempre("clienteId", null, pedido.getClienteId())
                .sempre("itens", null, String.join("; ", pedido.getDescricaoItens()))
                .sempre("valorBruto", null, pedido.getValorBruto())
                .sempre("valorFinal", null, pedido.getValorFinal())
                .sempre("status", null, pedido.getStatus())
                .sempre("criadoPor", null, pedido.getCriadoPor())
                .sempre("dataEntrada", null, pedido.getDataCriacao())
                .lista(), null, null);
        if (pedido.isDescontoSolicitado()) {
            auditoriaService.registrarAlteracao("SOLICITAR_DESCONTO", "Pedido", pedido.getId(), Alteracoes.nova()
                    .sempre("descontoSolicitado", false, true)
                    .lista(), pedido.getMotivoDesconto(), null);
        }
        return pedido;
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public Pedido solicitarCancelamento(String id, String motivo) {
        Pedido pedido = buscar(id);
        exigirAntesDaSaida(pedido, "solicitar cancelamento");
        if (pedido.isCancelamentoSolicitado()) {
            throw new RegraNegocioException("O cancelamento deste pedido já foi solicitado.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new RegraNegocioException("Informe o motivo do cancelamento.");
        }
        pedido.setCancelamentoSolicitado(true);
        pedido.setMotivoCancelamento(motivo.trim());
        pedido.setCancelamentoSolicitadoPor(usuarioLogado());
        pedido.setDataSolicitacaoCancelamento(LocalDateTime.now());
        pedidoRepository.save(pedido);

        auditoriaService.registrarAlteracao("SOLICITAR_CANCELAMENTO", "Pedido", id, Alteracoes.nova()
                .sempre("cancelamentoSolicitado", false, true)
                .lista(), motivo.trim(), null);
        return pedido;
    }

    // ========================
    // GERENTE (exclusivo)
    // ========================

    /**
     * Avança o pedido para o próximo status do fluxo. Transições fora da ordem são recusadas
     * (ex.: EM_PREPARACAO → FINALIZADO). Na saída (SAIU_PARA_ENTREGA / RETIRADO) registra
     * obrigatoriamente data/hora e responsável; na finalização, idem.
     */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido atualizarStatus(String id, String status) {
        StatusPedido destino;
        try {
            destino = StatusPedido.valueOf(status == null ? "" : status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RegraNegocioException("Status inválido.");
        }
        if (destino == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Use a ação 'Cancelar pedido' (com justificativa) para cancelar.");
        }

        Pedido pedido = buscar(id);
        StatusPedido atual = pedido.getStatusAtual();
        if (atual == null) {
            throw new RegraNegocioException("Status atual do pedido é desconhecido: " + pedido.getStatus());
        }
        if (atual == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode ter o status alterado.");
        }
        if (atual == StatusPedido.FINALIZADO) {
            throw new RegraNegocioException("Pedido finalizado não pode ter o status alterado.");
        }
        if (!atual.podeIrPara(destino)) {
            String permitidos = atual.proximos().stream().map(Enum::name).collect(Collectors.joining(" ou "));
            throw new RegraNegocioException("Transição inválida: " + atual.name() + " → " + destino.name()
                    + ". Próximo status permitido: " + permitidos + ".");
        }

        LocalDateTime agora = LocalDateTime.now();
        String usuario = usuarioLogado();
        String anterior = pedido.getStatus();
        pedido.setStatus(destino.name());
        String observacao = null;
        if (destino.isSaida()) {
            // Saída: data/hora e responsável obrigatórios
            pedido.setDataSaida(agora);
            pedido.setResponsavelSaida(usuario);
            pedido.setTipoSaida(destino.name());
            observacao = destino == StatusPedido.RETIRADO ? "Retirado pelo cliente" : "Saiu para entrega";
        } else if (destino == StatusPedido.FINALIZADO) {
            pedido.setDataFinalizacao(agora);
            pedido.setFinalizadoPor(usuario);
            observacao = "Atendimento concluído";
        }
        pedido.registrarHistorico(anterior, destino.name(), agora, usuario, observacao);
        pedidoRepository.save(pedido);

        Alteracoes campos = Alteracoes.nova().sempre("status", anterior, destino.name());
        if (destino.isSaida()) {
            campos.sempre("dataSaida", null, pedido.getDataSaida()).sempre("responsavelSaida", null, pedido.getResponsavelSaida());
        } else if (destino == StatusPedido.FINALIZADO) {
            campos.sempre("dataFinalizacao", null, pedido.getDataFinalizacao()).sempre("finalizadoPor", null, pedido.getFinalizadoPor());
        }
        auditoriaService.registrarAlteracao(destino.isSaida() ? "REGISTRAR_SAIDA" : "ALTERAR_STATUS", "Pedido", id,
                campos.lista(), null, observacao);
        return pedido;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, Double percentual) {
        return aplicarDesconto(id, percentual, null);
    }

    /**
     * Aplica (ou substitui) o desconto do pedido — exclusivo do GERENTE.
     * - Percentual digitado ou escolhido: maior que 0% e no máximo 20% do valor do pedido.
     * - Só antes da saída para entrega/retirada.
     * - Pode ser feito diretamente ou atendendo a uma solicitação do atendente (que fica atendida).
     * O total é sempre recalculado a partir dos itens + desconto; valores anteriores e novos vão para a auditoria.
     */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, Double percentual, String motivo) {
        if (percentual == null || percentual.isNaN() || percentual <= 0 || percentual > DESCONTO_MAXIMO) {
            throw new RegraNegocioException("O desconto deve ser maior que 0% e no máximo " + (int) DESCONTO_MAXIMO + "%.");
        }
        Pedido pedido = buscar(id);
        exigirAntesDaSaida(pedido, "aplicar desconto");
        if (pedido.getValorBruto() == null && (pedido.getItens() == null || pedido.getItens().isEmpty())) {
            throw new RegraNegocioException("Este pedido não tem valor cadastrado; não é possível aplicar desconto.");
        }
        Double percentualAntes = pedido.getDescontoPercentual();
        Double descontoAntes = pedido.getValorDesconto();
        Double finalAntes = pedido.getValorFinal();
        boolean atendeSolicitacao = pedido.isDescontoSolicitado();

        pedido.setDescontoPercentual(arredondar2(percentual));
        pedido.recalcularTotais(); // recalcula a partir dos itens + desconto autorizado
        pedido.setDescontoAplicadoPor(usuarioLogado());
        pedido.setDataDesconto(LocalDateTime.now());
        pedido.setDescontoSolicitado(false); // se havia solicitação, ela fica atendida
        pedidoRepository.save(pedido);

        // Justificativa: a digitada pelo gerente ou, se não houver, o motivo da solicitação do atendente
        String justificativa = motivo != null && !motivo.isBlank() ? motivo.trim() : pedido.getMotivoDesconto();
        Alteracoes campos = Alteracoes.nova()
                .sempre("descontoPercentual", percentualAntes, pedido.getDescontoPercentual())
                .sempre("valorDesconto", descontoAntes, pedido.getValorDesconto())
                .sempre("valorFinal", finalAntes, pedido.getValorFinal());
        if (atendeSolicitacao) {
            campos.sempre("descontoSolicitado", true, false);
        }
        auditoriaService.registrarAlteracao("APLICAR_DESCONTO", "Pedido", id, campos.lista(), justificativa,
                "Sobre R$ " + formatar(pedido.getValorBruto()) + "; "
                + (atendeSolicitacao ? "solicitado por " + pedido.getDescontoSolicitadoPor() : "aplicado diretamente pelo gerente"));
        return pedido;
    }

    // Recusa a solicitação de desconto: o valor do pedido continua o mesmo
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido rejeitarDesconto(String id) {
        Pedido pedido = buscar(id);
        if (!pedido.isDescontoSolicitado()) {
            throw new RegraNegocioException("Não há solicitação de desconto pendente para este pedido.");
        }
        pedido.setDescontoSolicitado(false);
        pedidoRepository.save(pedido);

        auditoriaService.registrarAlteracao("REJEITAR_DESCONTO", "Pedido", id, Alteracoes.nova()
                .sempre("descontoSolicitado", true, false)
                .lista(), null,
                "Solicitação de " + pedido.getDescontoSolicitadoPor() + " rejeitada; valor mantido em R$ "
                + formatar(pedido.getValorFinal()));
        return pedido;
    }

    /**
     * Cancela o pedido (estado excepcional). Exige justificativa, só é permitido antes da saída
     * e preserva todos os dados do pedido (itens, valores, histórico). Depois disso nada mais pode ser alterado.
     */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        if (justificativa == null || justificativa.trim().length() < JUSTIFICATIVA_MINIMA) {
            throw new RegraNegocioException("Informe a justificativa do cancelamento (mínimo de "
                    + JUSTIFICATIVA_MINIMA + " caracteres).");
        }
        Pedido pedido = buscar(id);
        exigirAntesDaSaida(pedido, "cancelar");

        LocalDateTime agora = LocalDateTime.now();
        String usuario = usuarioLogado();
        String anterior = pedido.getStatus();
        pedido.setStatusAntesDoCancelamento(anterior);
        pedido.setStatus(StatusPedido.CANCELADO.name());
        pedido.setJustificativaCancelamento(justificativa.trim());
        pedido.setCanceladoPor(usuario);
        pedido.setDataCancelamento(agora);
        pedido.setDescontoSolicitado(false); // solicitações pendentes deixam de valer
        pedido.registrarHistorico(anterior, StatusPedido.CANCELADO.name(), agora, usuario, justificativa.trim());
        pedidoRepository.save(pedido);

        auditoriaService.registrarAlteracao("CANCELAR_PEDIDO", "Pedido", id, Alteracoes.nova()
                .sempre("status", anterior, StatusPedido.CANCELADO.name())
                .sempre("canceladoPor", null, usuario)
                .sempre("dataCancelamento", null, agora)
                .lista(),
                justificativa.trim(),
                "Valor de R$ " + formatar(pedido.getValorFinal()) + " e itens preservados"
                + (pedido.isCancelamentoSolicitado() ? "; solicitado por " + pedido.getCancelamentoSolicitadoPor()
                        + " (" + pedido.getMotivoCancelamento() + ")" : ""));
        return pedido;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido rejeitarCancelamento(String id) {
        Pedido pedido = buscar(id);
        if (!pedido.isCancelamentoSolicitado() || StatusPedido.CANCELADO.name().equals(pedido.getStatus())) {
            throw new RegraNegocioException("Não há solicitação de cancelamento pendente para este pedido.");
        }
        String solicitante = pedido.getCancelamentoSolicitadoPor();
        pedido.setCancelamentoSolicitado(false);
        pedido.setCancelamentoSolicitadoPor(null);
        pedido.setDataSolicitacaoCancelamento(null);
        pedido.setMotivoCancelamento(null);
        pedidoRepository.save(pedido);

        auditoriaService.registrarAlteracao("REJEITAR_CANCELAMENTO", "Pedido", id, Alteracoes.nova()
                .sempre("cancelamentoSolicitado", true, false)
                .lista(), null, "Solicitação de " + solicitante + " rejeitada");
        return pedido;
    }

    // ========================
    // AUXILIARES
    // ========================
    private Pedido buscar(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    // Cancelamento e desconto só antes da saída para entrega/retirada (e nunca após cancelar/finalizar)
    private void exigirAntesDaSaida(Pedido pedido, String acao) {
        StatusPedido atual = pedido.getStatusAtual();
        if (atual == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode ser alterado.");
        }
        if (atual == null || !atual.isAntesDaSaida()) {
            throw new RegraNegocioException("Não é possível " + acao + ": o pedido já está "
                    + (atual == null ? pedido.getStatus() : atual.getDescricao().toLowerCase())
                    + ". Só é permitido antes da saída para entrega ou retirada.");
        }
    }

    private static double arredondar2(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    private static String formatar(Double valor) {
        return valor == null ? "—" : String.format("%.2f", valor);
    }

    private String usuarioLogado() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }
}
