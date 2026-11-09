package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.DashboardDto.Comparativo;
import com.umc.pediupatrao.dto.DashboardDto.FiltroAplicado;
import com.umc.pediupatrao.dto.DashboardDto.Opcao;
import com.umc.pediupatrao.dto.DashboardDto.OpcoesFiltro;
import com.umc.pediupatrao.dto.DashboardDto.PedidoRecente;
import com.umc.pediupatrao.dto.DashboardDto.PontoVenda;
import com.umc.pediupatrao.dto.DashboardDto.ProdutoRanking;
import com.umc.pediupatrao.dto.DashboardDto.Resumo;
import com.umc.pediupatrao.dto.DashboardDto.StatusQuantidade;
import com.umc.pediupatrao.dto.DashboardDto.VendaPorCategoria;
import com.umc.pediupatrao.dto.DashboardDto.VendaPorHora;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.DashboardCalculadora.Agrupamento;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Serviço do Dashboard Administrativo.
 *
 * Responsabilidades:
 *  1) validar e completar os filtros (RF05) — período padrão, limites, status e categoria válidos;
 *  2) buscar no banco os pedidos do período e os produtos (para saber a categoria de cada item);
 *  3) entregar os dados para a DashboardCalculadora e devolver DTOs prontos para a API.
 *
 * Somente ADMIN, GERENTE e o leitor técnico do Grafana (LEITOR_DASHBOARD) podem ver os números do estabelecimento
 * (também protegido por rota no SecurityConfig).
 */
@Service
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','LEITOR_DASHBOARD')")
public class DashboardService {

    public static final int DIAS_PADRAO = 30;
    public static final int DIAS_MAXIMOS = 366;
    public static final int LIMITE_MAXIMO = 50;

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;
    private final Clock clock;

    @Autowired // há dois construtores: este é o que o Spring deve usar
    public DashboardService(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository,
            ClienteRepository clienteRepository) {
        this(pedidoRepository, produtoRepository, clienteRepository, Clock.systemDefaultZone());
    }

    // Construtor com relógio fixo, usado nos testes para "congelar" a data de hoje
    DashboardService(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository,
            ClienteRepository clienteRepository, Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.clienteRepository = clienteRepository;
        this.clock = clock;
    }

    /** Filtros recebidos da requisição (todos opcionais). */
    public record Filtro(LocalDate inicio, LocalDate fim, String status, String categoria) {
    }

    // ===================== Endpoints =====================

    public Resumo resumo(Filtro filtro) {
        FiltroAplicado f = validar(filtro);
        DashboardCalculadora atual = calcular(f);

        // Período anterior com o mesmo número de dias (ex.: 01–30/11 compara com 02–31/10)
        long dias = ChronoUnit.DAYS.between(f.inicio(), f.fim()) + 1;
        LocalDate fimAnterior = f.inicio().minusDays(1);
        LocalDate inicioAnterior = fimAnterior.minusDays(dias - 1);
        DashboardCalculadora anterior = calcular(new FiltroAplicado(inicioAnterior, fimAnterior, f.status(), f.categoria()));

        Comparativo comparativo = new Comparativo(inicioAnterior, fimAnterior,
                anterior.faturamento(), anterior.pedidosValidos(),
                variacao(atual.faturamento(), anterior.faturamento()),
                variacao(atual.pedidosValidos(), anterior.pedidosValidos()));

        return new Resumo(f, atual.faturamento(), atual.totalPedidos(), atual.pedidosValidos(),
                atual.pedidosCancelados(), atual.ticketMedio(), atual.taxaCancelamento(), atual.itensVendidos(),
                atual.totalDescontos(), atual.tempoMedioSaidaMinutos(), comparativo);
    }

    public List<PontoVenda> vendas(Filtro filtro, String agrupamento) {
        FiltroAplicado f = validar(filtro);
        return calcular(f).serieDeVendas(f.inicio(), f.fim(), agrupamento(agrupamento));
    }

    public List<VendaPorHora> vendasPorHora(Filtro filtro) {
        return calcular(validar(filtro)).vendasPorHora();
    }

    public List<VendaPorCategoria> vendasPorCategoria(Filtro filtro) {
        return calcular(validar(filtro)).vendasPorCategoria();
    }

    public List<StatusQuantidade> pedidosPorStatus(Filtro filtro) {
        return calcular(validar(filtro)).pedidosPorStatus();
    }

    public List<ProdutoRanking> produtosMaisVendidos(Filtro filtro, Integer limite) {
        return calcular(validar(filtro)).produtosMaisVendidos(limite(limite, 5));
    }

    public List<PedidoRecente> pedidosRecentes(Filtro filtro, Integer limite) {
        DashboardCalculadora calc = calcular(validar(filtro));
        List<Pedido> pedidos = calc.pedidosMaisRecentes(limite(limite, 10));

        // Busca os nomes dos clientes de uma vez só (evita uma consulta por pedido)
        Set<String> ids = pedidos.stream().map(Pedido::getClienteId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> nomes = new HashMap<>();
        clienteRepository.findAllById(ids).forEach(c -> nomes.put(c.getId(), c.getNome()));

        return pedidos.stream().map(p -> {
            StatusPedido s = p.getStatusAtual();
            return new PedidoRecente(p.getId(), p.getDataCriacao(),
                    nomes.getOrDefault(p.getClienteId(), "Cliente não identificado"),
                    String.join(", ", p.getDescricaoItens()),
                    s == null ? p.getStatus() : s.name(),
                    s == null ? "Desconhecido" : s.getDescricao(),
                    calc.valorDoPedido(p));
        }).toList();
    }

    public OpcoesFiltro opcoesDeFiltro() {
        List<Opcao> status = Arrays.stream(StatusPedido.values())
                .map(s -> new Opcao(s.name(), s.getDescricao())).toList();
        LocalDate hoje = LocalDate.now(clock);
        return new OpcoesFiltro(status, List.copyOf(categorias()), hoje.minusDays(DIAS_PADRAO - 1L), hoje, DIAS_MAXIMOS);
    }

    // ===================== Validação dos filtros (RF05 / RF07) =====================

    FiltroAplicado validar(Filtro filtro) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate fim = filtro.fim() != null ? filtro.fim() : hoje;
        LocalDate inicio = filtro.inicio() != null ? filtro.inicio() : fim.minusDays(DIAS_PADRAO - 1L);

        if (inicio.isAfter(fim)) {
            throw new RegraNegocioException("A data inicial não pode ser posterior à data final.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) + 1 > DIAS_MAXIMOS) {
            throw new RegraNegocioException("O período máximo de consulta é de " + DIAS_MAXIMOS + " dias.");
        }

        String status = null;
        if (!semFiltro(filtro.status())) {
            status = StatusPedido.de(filtro.status())
                    .orElseThrow(() -> new RegraNegocioException("Status inválido: " + filtro.status()))
                    .name();
        }

        String categoria = null;
        if (!semFiltro(filtro.categoria())) {
            categoria = categorias().stream()
                    .filter(c -> c.equalsIgnoreCase(filtro.categoria().trim()))
                    .findFirst()
                    .orElseThrow(() -> new RegraNegocioException("Categoria inválida: " + filtro.categoria()));
        }
        return new FiltroAplicado(inicio, fim, status, categoria);
    }

    // Vazio, "todos" ou "todas" = sem filtro (o Grafana envia "todos" quando a opção "Todos" está selecionada)
    private static boolean semFiltro(String valor) {
        return valor == null || valor.isBlank() || valor.equalsIgnoreCase("todos") || valor.equalsIgnoreCase("todas");
    }

    private static Agrupamento agrupamento(String valor) {
        if (valor == null || valor.isBlank()) {
            return Agrupamento.DIA;
        }
        try {
            return Agrupamento.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RegraNegocioException("Agrupamento inválido. Use DIA, SEMANA ou MES.");
        }
    }

    private static int limite(Integer valor, int padrao) {
        if (valor == null) {
            return padrao;
        }
        if (valor < 1 || valor > LIMITE_MAXIMO) {
            throw new RegraNegocioException("O limite deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        return valor;
    }

    // ===================== Acesso aos dados =====================

    private DashboardCalculadora calcular(FiltroAplicado f) {
        // fim + 1 dia à meia-noite: inclui o dia final inteiro
        List<Pedido> pedidos = pedidoRepository.buscarPorPeriodo(f.inicio().atStartOfDay(), f.fim().plusDays(1).atStartOfDay());
        StatusPedido status = f.status() == null ? null : StatusPedido.valueOf(f.status());
        return new DashboardCalculadora(pedidos, categoriaDosProdutos(), status, f.categoria());
    }

    private Map<String, String> categoriaDosProdutos() {
        Map<String, String> mapa = new HashMap<>();
        for (Produto p : produtoRepository.findAll()) {
            mapa.put(p.getId(), categoriaDe(p));
        }
        return mapa;
    }

    private Set<String> categorias() {
        Set<String> categorias = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        produtoRepository.findAll().forEach(p -> categorias.add(categoriaDe(p)));
        return categorias;
    }

    private static String categoriaDe(Produto p) {
        return p.getTipo() == null || p.getTipo().isBlank() ? DashboardCalculadora.SEM_CATEGORIA : p.getTipo().trim();
    }

    /** Variação percentual: (atual - anterior) / anterior x 100. Null se o anterior for zero. */
    static Double variacao(double atual, double anterior) {
        if (anterior == 0) {
            return null;
        }
        return BigDecimal.valueOf((atual - anterior) * 100 / anterior).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
