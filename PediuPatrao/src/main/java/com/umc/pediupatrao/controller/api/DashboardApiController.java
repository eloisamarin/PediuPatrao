package com.umc.pediupatrao.controller.api;

import com.umc.pediupatrao.dto.DashboardDto.OpcoesFiltro;
import com.umc.pediupatrao.dto.DashboardDto.PedidoRecente;
import com.umc.pediupatrao.dto.DashboardDto.PontoVenda;
import com.umc.pediupatrao.dto.DashboardDto.ProdutoRanking;
import com.umc.pediupatrao.dto.DashboardDto.Resumo;
import com.umc.pediupatrao.dto.DashboardDto.StatusQuantidade;
import com.umc.pediupatrao.dto.DashboardDto.VendaPorCategoria;
import com.umc.pediupatrao.dto.DashboardDto.VendaPorHora;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.DashboardService.Filtro;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardApiController {

    private final DashboardService dashboardService;

    public DashboardApiController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /// Indicadores gerais + comparação com o período anterior
    @GetMapping("/resumo")
    public Resumo resumo(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria) {
        return dashboardService.resumo(new Filtro(inicio, fim, status, categoria));
    }

    /// Vendas filtrada por Dia, Semana e Mes
    @GetMapping("/vendas")
    public List<PontoVenda> vendas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String agrupamento) {
        return dashboardService.vendas(new Filtro(inicio, fim, status, categoria), agrupamento);
    }

    /// Vendas por hora do dia
    @GetMapping("/vendas-por-hora")
    public List<VendaPorHora> vendasPorHora(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria) {
        return dashboardService.vendasPorHora(new Filtro(inicio, fim, status, categoria));
    }

    /// Faturamento por categoria de produto
    @GetMapping("/vendas-por-categoria")
    public List<VendaPorCategoria> vendasPorCategoria(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria) {
        return dashboardService.vendasPorCategoria(new Filtro(inicio, fim, status, categoria));
    }

    /// Quantidade de pedidos por status
    @GetMapping("/status")
    public List<StatusQuantidade> status(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria) {
        return dashboardService.pedidosPorStatus(new Filtro(inicio, fim, status, categoria));
    }

    /// Últimos pedidos do período
    @GetMapping("/pedidos-recentes")
    public List<PedidoRecente> pedidosRecentes(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.pedidosRecentes(new Filtro(inicio, fim, status, categoria), limite);
    }

    /// Ranking dos produtos mais vendidos (padrão: 5, máximo: 50)
    @GetMapping("/produtos-mais-vendidos")
    public List<ProdutoRanking> produtosMaisVendidos(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.produtosMaisVendidos(new Filtro(inicio, fim, status, categoria), limite);
    }

    /// Opções para montar os filtros da tela (status, categorias, período padrão)
    @GetMapping("/filtros")
    public OpcoesFiltro filtros() {
        return dashboardService.opcoesDeFiltro();
    }
}
