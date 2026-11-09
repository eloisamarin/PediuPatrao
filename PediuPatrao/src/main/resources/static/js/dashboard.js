/*
 * Dashboard Administrativo — Pediu Patrão
 *
 * Fluxo:
 *  1) carrega as opções de filtro (GET /api/dashboard/filtros);
 *  2) lê os filtros da tela e chama os endpoints da API em paralelo (Promise.all);
 *  3) desenha os cards, gráficos (Chart.js) e tabelas;
 *  4) repete a cada 60 segundos se "Auto" estiver ligado (RF07).
 *
 * Os filtros ficam também na URL (?inicio=...&fim=...), então dá para atualizar a página
 * ou compartilhar o link sem perder a consulta.
 */
(function () {
    'use strict';

    const API = '/api/dashboard';
    const INTERVALO_ATUALIZACAO = 60_000;
    const CORES_STATUS = {
        RECEBIDO: '#0d6efd', EM_PREPARACAO: '#fd7e14', PRONTO: '#20c997', SAIU_PARA_ENTREGA: '#6f42c1',
        RETIRADO: '#0dcaf0', FINALIZADO: '#198754', CANCELADO: '#dc3545'
    };
    const PALETA = ['#0d6efd', '#fd7e14', '#198754', '#6f42c1', '#d63384', '#20c997', '#ffc107', '#6c757d'];

    const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    const numero = new Intl.NumberFormat('pt-BR');
    const $ = (id) => document.getElementById(id);

    const graficos = {};      // instâncias do Chart.js (reaproveitadas a cada atualização)
    let diasMaximos = 366;
    let timer = null;
    let requisicaoAtual = null; // AbortController: cancela a consulta anterior se o usuário filtrar de novo

    // ===================== Inicialização =====================

    document.addEventListener('DOMContentLoaded', iniciar);
    if (document.readyState !== 'loading') {
        iniciar();
    }

    let iniciado = false;
    async function iniciar() {
        if (iniciado) return;
        iniciado = true;

        $('formFiltros').addEventListener('submit', (e) => { e.preventDefault(); carregar(); });
        $('btnAtualizar').addEventListener('click', () => carregar());
        $('btnTentarNovamente').addEventListener('click', () => carregar());
        $('btnLimpar').addEventListener('click', limparFiltros);
        $('autoAtualizar').addEventListener('change', agendarAtualizacao);
        $('fAgrupamento').addEventListener('change', () => carregar());
        document.querySelectorAll('#atalhos [data-dias], #atalhos [data-mes]').forEach((b) =>
            b.addEventListener('click', () => aplicarAtalho(b)));

        try {
            const opcoes = await buscar(`${API}/filtros`);
            montarFiltros(opcoes);
        } catch (e) {
            mostrarErro(e.message);
            return;
        }
        await carregar();
        agendarAtualizacao();
    }

    function montarFiltros(opcoes) {
        diasMaximos = opcoes.diasMaximos;
        opcoes.status.forEach((s) => $('fStatus').add(new Option(s.descricao, s.valor)));
        opcoes.categorias.forEach((c) => $('fCategoria').add(new Option(c, c)));
        $('fInicio').dataset.padrao = opcoes.inicioPadrao;
        $('fFim').dataset.padrao = opcoes.fimPadrao;

        // Valores vindos da URL têm prioridade sobre o padrão
        const url = new URLSearchParams(location.search);
        $('fInicio').value = url.get('inicio') || opcoes.inicioPadrao;
        $('fFim').value = url.get('fim') || opcoes.fimPadrao;
        $('fStatus').value = url.get('status') || '';
        $('fCategoria').value = url.get('categoria') || '';
        $('fAgrupamento').value = url.get('agrupamento') || 'DIA';
    }

    function limparFiltros() {
        $('fInicio').value = $('fInicio').dataset.padrao;
        $('fFim').value = $('fFim').dataset.padrao;
        $('fStatus').value = '';
        $('fCategoria').value = '';
        $('fAgrupamento').value = 'DIA';
        carregar();
    }

    function aplicarAtalho(botao) {
        const hoje = new Date();
        let inicio = new Date(hoje);
        if (botao.dataset.mes) {
            inicio = new Date(hoje.getFullYear(), hoje.getMonth(), 1);
        } else {
            inicio.setDate(hoje.getDate() - (Number(botao.dataset.dias) - 1));
        }
        $('fInicio').value = dataISO(inicio);
        $('fFim').value = dataISO(hoje);
        $('fAgrupamento').value = Number(botao.dataset.dias) > 60 ? 'SEMANA' : 'DIA';
        carregar();
    }

    function agendarAtualizacao() {
        clearInterval(timer);
        if ($('autoAtualizar').checked) {
            // Só atualiza se a aba estiver visível (economiza requisições)
            timer = setInterval(() => { if (!document.hidden) carregar(true); }, INTERVALO_ATUALIZACAO);
        }
    }

    // ===================== Filtros (RF05) =====================

    /** Lê e valida os filtros. Retorna null (e mostra a mensagem) se houver erro. */
    function lerFiltros() {
        const inicio = $('fInicio').value;
        const fim = $('fFim').value;
        let erro = '';
        if (!inicio || !fim) {
            erro = 'Informe a data inicial e a data final.';
        } else if (inicio > fim) {
            erro = 'A data inicial não pode ser posterior à data final.';
        } else if ((new Date(fim) - new Date(inicio)) / 86_400_000 + 1 > diasMaximos) {
            erro = `O período máximo é de ${diasMaximos} dias.`;
        }
        $('erroFiltro').textContent = erro;
        $('fInicio').classList.toggle('is-invalid', !!erro);
        $('fFim').classList.toggle('is-invalid', !!erro);
        if (erro) return null;

        const params = new URLSearchParams({ inicio, fim });
        if ($('fStatus').value) params.set('status', $('fStatus').value);
        if ($('fCategoria').value) params.set('categoria', $('fCategoria').value);
        return params;
    }

    // ===================== Integração com a API (RF06) =====================

    /** GET na API com tratamento de erros padronizado. */
    async function buscar(url, signal) {
        let resposta;
        try {
            resposta = await fetch(url, { headers: { Accept: 'application/json' }, credentials: 'same-origin', signal });
        } catch (e) {
            if (e.name === 'AbortError') throw e;
            throw new Error('Não foi possível conectar ao servidor. Verifique sua conexão.');
        }
        if (resposta.status === 401) {
            location.href = '/login?expirada';
            throw new Error('Sessão expirada.');
        }
        if (resposta.status === 403) throw new Error('Você não tem permissão para ver o dashboard.');
        if (!resposta.ok) {
            const corpo = await resposta.json().catch(() => ({}));
            throw new Error(corpo.erro || `Erro ao consultar a API (HTTP ${resposta.status}).`);
        }
        return resposta.json();
    }

    /** Busca todos os dados do painel em paralelo e redesenha a tela. */
    async function carregar(silencioso = false) {
        const params = lerFiltros();
        if (!params) return;

        history.replaceState(null, '', `?${params}&agrupamento=${$('fAgrupamento').value}`);
        if (requisicaoAtual) requisicaoAtual.abort();
        requisicaoAtual = new AbortController();
        const { signal } = requisicaoAtual;

        if (!silencioso) $('carregando').classList.remove('d-none');
        try {
            const q = params.toString();
            const [resumo, vendas, status, produtos, horas, categorias, pedidos] = await Promise.all([
                buscar(`${API}/resumo?${q}`, signal),
                buscar(`${API}/vendas?${q}&agrupamento=${$('fAgrupamento').value}`, signal),
                buscar(`${API}/status?${q}`, signal),
                buscar(`${API}/produtos-mais-vendidos?${q}&limite=5`, signal),
                buscar(`${API}/vendas-por-hora?${q}`, signal),
                buscar(`${API}/vendas-por-categoria?${q}`, signal),
                buscar(`${API}/pedidos-recentes?${q}&limite=10`, signal)
            ]);
            esconderErro();
            desenharResumo(resumo, horas, produtos);
            desenharVendas(vendas);
            desenharStatus(status);
            desenharProdutos(produtos);
            desenharHoras(horas);
            desenharCategorias(categorias);
            desenharPedidos(pedidos);
            $('ultimaAtualizacao').textContent = 'Atualizado às ' + new Date().toLocaleTimeString('pt-BR');
        } catch (e) {
            if (e.name !== 'AbortError') mostrarErro(e.message);
        } finally {
            if (!signal.aborted) $('carregando').classList.add('d-none');
        }
    }

    // ===================== Desenho: indicadores (RF01) =====================

    function desenharResumo(r, horas, produtos) {
        $('kpiFaturamento').textContent = moeda.format(r.faturamento);
        $('kpiPedidos').textContent = numero.format(r.pedidosValidos);
        $('kpiTicket').textContent = moeda.format(r.ticketMedio);
        $('kpiItens').textContent = `${numero.format(r.itensVendidos)} itens vendidos`;
        $('kpiCancelados').textContent = numero.format(r.pedidosCancelados);
        $('kpiTaxaCancelamento').textContent = `${r.taxaCancelamento.toLocaleString('pt-BR')}% dos ${numero.format(r.totalPedidos)} pedidos`;
        $('kpiDescontos').textContent = moeda.format(r.totalDescontos);
        $('kpiTempo').textContent = r.tempoMedioSaidaMinutos == null ? 'Sem dados' : formatarMinutos(r.tempoMedioSaidaMinutos);
        variacao($('kpiFaturamentoVar'), r.comparativo.variacaoFaturamento);
        variacao($('kpiPedidosVar'), r.comparativo.variacaoPedidos);

        const pico = horas.reduce((a, b) => (b.pedidos > a.pedidos ? b : a), { pedidos: 0 });
        $('kpiPico').textContent = pico.pedidos ? `${pico.hora}h às ${pico.hora + 1}h (${pico.pedidos} pedidos)` : 'Sem dados';
        $('kpiCampeao').textContent = produtos.length ? `${produtos[0].nome} (${produtos[0].quantidade} un.)` : 'Sem dados';
        $('kpiCampeao').title = $('kpiCampeao').textContent;
    }

    function variacao(el, valor) {
        if (valor == null) {
            el.className = 'small text-muted';
            el.textContent = 'sem base no período anterior';
            return;
        }
        const sobe = valor >= 0;
        el.className = 'small ' + (sobe ? 'text-success' : 'text-danger');
        el.innerHTML = `<i class="bi bi-arrow-${sobe ? 'up' : 'down'}-right"></i> ${Math.abs(valor).toLocaleString('pt-BR')}% vs. período anterior`;
    }

    // ===================== Desenho: gráficos (RF02, RF03, RF04) =====================

    /** Cria o gráfico na primeira vez e só troca os dados nas próximas (evita "piscar"). */
    function grafico(id, config, vazio) {
        const canvas = $(id);
        let aviso = canvas.parentElement.querySelector('.dash-vazio');
        if (!aviso) {
            aviso = document.createElement('div');
            aviso.className = 'dash-vazio d-none';
            aviso.textContent = 'Nenhum dado para os filtros selecionados.';
            canvas.parentElement.appendChild(aviso);
        }
        aviso.classList.toggle('d-none', !vazio);
        canvas.classList.toggle('invisible', vazio);

        if (graficos[id]) {
            graficos[id].data = config.data;
            graficos[id].update();
        } else {
            config.options = Object.assign({ responsive: true, maintainAspectRatio: false }, config.options);
            graficos[id] = new Chart(canvas, config);
        }
    }

    function desenharVendas(serie) {
        grafico('graficoVendas', {
            type: 'bar',
            data: {
                labels: serie.map((p) => p.rotulo),
                datasets: [
                    { type: 'line', label: 'Faturamento (R$)', data: serie.map((p) => p.faturamento), yAxisID: 'y',
                      borderColor: '#198754', backgroundColor: 'rgba(25,135,84,.12)', fill: true, tension: .3, pointRadius: 2 },
                    { type: 'bar', label: 'Pedidos', data: serie.map((p) => p.pedidos), yAxisID: 'y1',
                      backgroundColor: 'rgba(13,110,253,.35)', borderRadius: 3 }
                ]
            },
            options: {
                interaction: { mode: 'index', intersect: false },
                scales: {
                    y: { beginAtZero: true, position: 'left', ticks: { callback: (v) => moeda.format(v) } },
                    y1: { beginAtZero: true, position: 'right', grid: { drawOnChartArea: false }, ticks: { precision: 0 } }
                },
                plugins: { tooltip: { callbacks: { label: rotuloTooltip } } }
            }
        }, serie.every((p) => p.pedidos === 0));
    }

    function desenharStatus(lista) {
        const comPedidos = lista.filter((s) => s.quantidade > 0);
        grafico('graficoStatus', {
            type: 'doughnut',
            data: {
                labels: comPedidos.map((s) => s.descricao),
                datasets: [{ data: comPedidos.map((s) => s.quantidade), backgroundColor: comPedidos.map((s) => CORES_STATUS[s.status]) }]
            },
            options: {
                plugins: {
                    legend: { position: 'bottom', labels: { boxWidth: 12 } },
                    tooltip: { callbacks: { label: (c) => ` ${c.label}: ${c.raw} (${comPedidos[c.dataIndex].percentual.toLocaleString('pt-BR')}%)` } }
                }
            }
        }, comPedidos.length === 0);
    }

    function desenharProdutos(ranking) {
        grafico('graficoProdutos', {
            type: 'bar',
            data: {
                labels: ranking.map((p) => p.nome),
                datasets: [{ label: 'Quantidade vendida', data: ranking.map((p) => p.quantidade), backgroundColor: PALETA, borderRadius: 4 }]
            },
            options: { indexAxis: 'y', plugins: { legend: { display: false } }, scales: { x: { beginAtZero: true, ticks: { precision: 0 } } } }
        }, ranking.length === 0);

        $('tabelaProdutos').innerHTML = ranking.length
            ? ranking.map((p) => `<tr><td>${p.posicao}º</td><td>${esc(p.nome)}</td><td><span class="badge bg-light text-dark border">${esc(p.categoria)}</span></td>
                 <td class="text-end">${numero.format(p.quantidade)}</td><td class="text-end">${moeda.format(p.faturamento)}</td></tr>`).join('')
            : '<tr><td colspan="5" class="text-center text-muted">Nenhum produto vendido no período.</td></tr>';
    }

    function desenharHoras(horas) {
        grafico('graficoHoras', {
            type: 'bar',
            data: {
                labels: horas.map((h) => `${h.hora}h`),
                datasets: [{ label: 'Pedidos', data: horas.map((h) => h.pedidos), backgroundColor: 'rgba(253,126,20,.6)', borderRadius: 3 }]
            },
            options: { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } }
        }, horas.every((h) => h.pedidos === 0));
    }

    function desenharCategorias(lista) {
        grafico('graficoCategorias', {
            type: 'bar',
            data: {
                labels: lista.map((c) => c.categoria),
                datasets: [{ label: 'Faturamento (R$)', data: lista.map((c) => c.faturamento), backgroundColor: PALETA, borderRadius: 4 }]
            },
            options: {
                indexAxis: 'y',
                plugins: { legend: { display: false }, tooltip: { callbacks: { label: (c) => ` ${moeda.format(c.raw)} — ${lista[c.dataIndex].quantidade} un.` } } },
                scales: { x: { beginAtZero: true, ticks: { callback: (v) => moeda.format(v) } } }
            }
        }, lista.length === 0);
    }

    // ===================== Desenho: tabela de pedidos (RF03) =====================

    function desenharPedidos(pedidos) {
        $('tabelaPedidos').innerHTML = pedidos.length
            ? pedidos.map((p) => `<tr>
                <td class="text-nowrap">${p.dataCriacao ? new Date(p.dataCriacao).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—'}</td>
                <td>${esc(p.cliente)}</td>
                <td class="small">${esc(p.itens)}</td>
                <td><span class="badge" style="background:${CORES_STATUS[p.status] || '#6c757d'}">${esc(p.descricaoStatus)}</span></td>
                <td class="text-end text-nowrap">${moeda.format(p.valor)}</td></tr>`).join('')
            : '<tr><td colspan="5" class="text-center text-muted py-3">Nenhum pedido encontrado para os filtros selecionados.</td></tr>';
    }

    // ===================== Auxiliares =====================

    function rotuloTooltip(c) {
        return c.dataset.yAxisID === 'y' ? ` Faturamento: ${moeda.format(c.raw)}` : ` Pedidos: ${c.raw}`;
    }

    function formatarMinutos(min) {
        if (min < 60) return `${Math.round(min)} min`;
        return `${Math.floor(min / 60)}h ${Math.round(min % 60)}min`;
    }

    function dataISO(d) {
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    }

    // Escapa texto vindo do banco antes de colocar no HTML (evita XSS com nomes de clientes/produtos)
    function esc(texto) {
        const div = document.createElement('div');
        div.textContent = texto == null ? '' : String(texto);
        return div.innerHTML;
    }

    function mostrarErro(msg) {
        $('textoErro').textContent = msg;
        $('alertaErro').classList.remove('d-none');
    }

    function esconderErro() {
        $('alertaErro').classList.add('d-none');
    }
})();
