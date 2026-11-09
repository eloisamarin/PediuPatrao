package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.DashboardDto.FiltroAplicado;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.DashboardService.Filtro;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Validação dos filtros do dashboard (RF05) e cálculo da variação. */
class DashboardServiceTest {

    private DashboardService service;

    @BeforeEach
    void setUp() {
        ProdutoRepository produtos = mock(ProdutoRepository.class);
        Produto pizza = new Produto();
        pizza.setId("p1");
        pizza.setTipo("Pizza");
        when(produtos.findAll()).thenReturn(List.of(pizza));
        ZoneId zona = ZoneId.systemDefault();
        Clock hoje = Clock.fixed(LocalDate.of(2026, 11, 7).atStartOfDay(zona).toInstant(), zona);
        service = new DashboardService(mock(PedidoRepository.class), produtos, mock(ClienteRepository.class), hoje);
    }

    @Test
    void periodoPadraoSaoOsUltimos30Dias() {
        FiltroAplicado f = service.validar(new Filtro(null, null, null, null));
        assertEquals(LocalDate.of(2026, 10, 9), f.inicio());
        assertEquals(LocalDate.of(2026, 11, 7), f.fim());
    }

    @Test
    void normalizaStatusECategoria() {
        FiltroAplicado f = service.validar(new Filtro(null, null, "em_preparo", "pizza"));
        assertEquals("EM_PREPARACAO", f.status());
        assertEquals("Pizza", f.categoria());
    }

    @Test
    void todosETodasSignificamSemFiltro() {
        FiltroAplicado f = service.validar(new Filtro(null, null, "todos", "TODAS"));
        assertNull(f.status());
        assertNull(f.categoria());
    }

    @Test
    void rejeitaFiltrosInvalidos() {
        assertThrows(RegraNegocioException.class,
                () -> service.validar(new Filtro(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 1), null, null)));
        assertThrows(RegraNegocioException.class,
                () -> service.validar(new Filtro(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 7), null, null)));
        assertThrows(RegraNegocioException.class, () -> service.validar(new Filtro(null, null, "XYZ", null)));
        assertThrows(RegraNegocioException.class, () -> service.validar(new Filtro(null, null, null, "Sushi")));
    }

    @Test
    void variacaoPercentual() {
        assertEquals(50.0, DashboardService.variacao(150, 100));
        assertEquals(-25.0, DashboardService.variacao(75, 100));
        assertNull(DashboardService.variacao(10, 0));
    }
}
