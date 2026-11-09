(function () {
    'use strict';

    const API = '/api/dashboard';
    const INTERVALO_ATUALIZACAO_MS = 20000;
    const CAMPOS_FILTRO = ['dataInicio', 'dataFim', 'status', 'categoria'];

    const STATUS = {
        RECEBIDO: { rotulo: 'Recebido', cor: '#6c757d', classe: 'bg-secondary' },
        EM_PREPARACAO: { rotulo: 'Em preparação', cor: '#ffc107', classe: 'bg-warning' },
        PRONTO: { rotulo: 'Pronto', cor: '#17a2b8', classe: 'bg-info' },
        SAIU_PARA_ENTREGA: { rotulo: 'Saiu para entrega', cor: '#007bff', classe: 'bg-primary' },
        RETIRADO: { rotulo: 'Retirado', cor: '#6610f2', classe: 'bg-indigo' },
        FINALIZADO: { rotulo: 'Finalizado', cor: '#28a745', classe: 'bg-success' },
        CANCELADO: { rotulo: 'Cancelado', cor: '#dc3545', classe: 'bg-danger' }
    };
    const CORES = ['#007bff', '#28a745', '#ffc107', '#dc3545', '#17a2b8', '#6610f2', '#fd7e14', '#20c997', '#e83e8c', '#6c757d'];

    const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    const numero = new Intl.NumberFormat('pt-BR');
    const decimal = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
    const dataHora = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

    const graficos = {};
    let temporizador = null;

    toastr.options = { positionClass: 'toast-bottom-right', timeOut: 6000, preventDuplicates: true };

    // Filtros
    function campo(id) {
        return document.getElementById(id);
    }

    function formatarData(data) {
        const mes = String(data.getMonth() + 1).padStart(2, '0');
        const dia = String(data.getDate()).padStart(2, '0');
        return data.getFullYear() + '-' + mes + '-' + dia;
    }

    function exibirData(iso) {
        const [ano, mes, dia] = iso.split('-');
        return dia + '/' + mes + '/' + ano;
    }

    function periodoPadrao() {
        const hoje = new Date();
        const inicio = new Date(hoje);
        inicio.setDate(hoje.getDate() - 29);
        campo('dataInicio').value = formatarData(inicio);
        campo('dataFim').value = formatarData(hoje);
    }

    function lerFiltros() {
        const filtros = {};
        CAMPOS_FILTRO.forEach(nome => {
            if (campo(nome).value) {
                filtros[nome] = campo(nome).value;
            }
        });
        return filtros;
    }

    function preencherSelect(id, valores, rotulo) {
        const select = campo(id);
        valores.forEach(valor => {
            const opcao = document.createElement('option');
            opcao.value = valor;
            opcao.textContent = rotulo ? rotulo(valor) : valor;
            select.appendChild(opcao);
        });
    }

    // Comunicação com a API
    async function buscar(caminho, parametros) {
        const query = new URLSearchParams(parametros || {}).toString();
        let resposta;
        try {
            resposta = await fetch(API + caminho + (query ? '?' + query : ''), {
                headers: { Accept: 'application/json' },
                cache: 'no-store'
            });
        } catch (e) {
            throw new Error('Não foi possível conectar ao servidor.');
        }

        if (resposta.redirected && resposta.url.includes('/login')) {
            window.location.href = '/login';
            throw new Error('Sessão expirada. Faça login novamente.');
        }
        if (!resposta.ok) {
            const corpo = await resposta.json().catch(() => null);
            throw new Error(corpo && corpo.erro ? corpo.erro : 'Erro ' + resposta.status + ' ao consultar ' + caminho + '.');
        }
        return resposta.json();
    }

    // Estados dos cards
    function carregando(idCard, ativo) {
        const card = campo(idCard);
        const overlay = card.querySelector(':scope > .overlay');
        if (ativo && !overlay) {
            const div = document.createElement('div');
            div.className = 'overlay';
            div.innerHTML = '<i class="fas fa-2x fa-sync-alt fa-spin"></i>';
            card.appendChild(div);
        } else if (!ativo && overlay) {
            overlay.remove();
        }
    }

    function mensagemCard(idCard, texto, erro) {
        const card = campo(idCard);
        const mensagem = card.querySelector('.mensagem-card');
        const conteudo = card.querySelector('.conteudo-card');
        if (texto) {
            mensagem.textContent = texto;
            mensagem.classList.toggle('text-danger', !!erro);
            mensagem.classList.remove('d-none');
            conteudo.classList.add('d-none');
        } else {
            mensagem.classList.add('d-none');
            conteudo.classList.remove('d-none');
        }
    }

    function escapar(texto) {
        const div = document.createElement('div');
        div.textContent = texto == null ? '' : String(texto);
        return div.innerHTML;
    }

    function desenharGrafico(id, configuracao) {
        if (graficos[id]) {
            graficos[id].destroy();
        }
        graficos[id] = new Chart(campo(id).getContext('2d'), configuracao);
    }

    // Indicadores
    function renderizarIndicadores(d) {
        campo('periodo-exibido').textContent = 'Período: ' + exibirData(d.dataInicio) + ' a ' + exibirData(d.dataFim);
        campo('ind-faturamento').textContent = moeda.format(d.faturamento);
        campo('ind-pedidos').textContent = numero.format(d.pedidosValidos);
        campo('ind-total').textContent = numero.format(d.totalPedidos);
        campo('ind-ticket').textContent = moeda.format(d.ticketMedio);
        campo('ind-cancelados').textContent = numero.format(d.pedidosCancelados)
            + ' (' + decimal.format(d.taxaCancelamento) + '%)';
    }

    // Vendas
    function renderizarVendas(d) {
        const nomes = { DIA: 'Por dia', SEMANA: 'Por semana', MES: 'Por mês' };
        campo('agrupamento-usado').textContent = nomes[d.agrupamento] || '';
        const temVendas = d.series.some(s => s.pedidos > 0 || s.cancelados > 0);
        mensagemCard('card-vendas', temVendas ? '' : 'Nenhuma venda no período selecionado.');
        if (!temVendas) {
            return;
        }
        desenharGrafico('grafico-vendas', {
            type: 'bar',
            data: {
                labels: d.series.map(s => s.rotulo),
                datasets: [
                    {
                        type: 'line',
                        label: 'Faturamento',
                        data: d.series.map(s => s.faturamento),
                        yAxisID: 'faturamento',
                        borderColor: '#007bff',
                        backgroundColor: 'rgba(0, 123, 255, 0.1)',
                        lineTension: 0.3,
                        fill: true
                    },
                    {
                        label: 'Pedidos',
                        data: d.series.map(s => s.pedidos),
                        yAxisID: 'pedidos',
                        backgroundColor: 'rgba(40, 167, 69, 0.6)'
                    },
                    {
                        label: 'Cancelados',
                        data: d.series.map(s => s.cancelados),
                        yAxisID: 'pedidos',
                        backgroundColor: 'rgba(220, 53, 69, 0.6)'
                    }
                ]
            },
            options: {
                maintainAspectRatio: false,
                tooltips: {
                    mode: 'index',
                    intersect: false,
                    callbacks: {
                        label: (item, dados) => {
                            const ds = dados.datasets[item.datasetIndex];
                            const valor = ds.yAxisID === 'faturamento' ? moeda.format(item.yLabel) : numero.format(item.yLabel);
                            return ds.label + ': ' + valor;
                        }
                    }
                },
                scales: {
                    yAxes: [
                        { id: 'faturamento', position: 'left', ticks: { beginAtZero: true, callback: v => moeda.format(v) } },
                        { id: 'pedidos', position: 'right', gridLines: { drawOnChartArea: false }, ticks: { beginAtZero: true, precision: 0 } }
                    ]
                }
            }
        });
    }

    function renderizarCategorias(lista) {
        mensagemCard('card-categorias', lista.length ? '' : 'Nenhuma venda no período selecionado.');
        if (!lista.length) {
            return;
        }
        desenharGrafico('grafico-categorias', {
            type: 'doughnut',
            data: {
                labels: lista.map(c => c.categoria),
                datasets: [{ data: lista.map(c => c.faturamento), backgroundColor: lista.map((c, i) => CORES[i % CORES.length]) }]
            },
            options: {
                maintainAspectRatio: false,
                legend: { position: 'right' },
                tooltips: {
                    callbacks: {
                        label: (item) => {
                            const c = lista[item.index];
                            return c.categoria + ': ' + moeda.format(c.faturamento) + ' (' + decimal.format(c.percentual) + '%)';
                        }
                    }
                }
            }
        });
    }

    // Situação dos pedidos
    function badgeStatus(status) {
        const info = STATUS[status] || { rotulo: status, classe: 'bg-secondary' };
        return '<span class="badge ' + info.classe + '">' + escapar(info.rotulo) + '</span>';
    }

    function renderizarStatus(lista) {
        const total = lista.reduce((soma, s) => soma + s.quantidade, 0);
        mensagemCard('card-status', total ? '' : 'Nenhum pedido no período selecionado.');
        if (!total) {
            return;
        }
        const comPedidos = lista.filter(s => s.quantidade > 0);
        desenharGrafico('grafico-status', {
            type: 'doughnut',
            data: {
                labels: comPedidos.map(s => STATUS[s.status].rotulo),
                datasets: [{
                    data: comPedidos.map(s => s.quantidade),
                    backgroundColor: comPedidos.map(s => STATUS[s.status].cor)
                }]
            },
            options: { maintainAspectRatio: false, legend: { display: false } }
        });

        campo('lista-status').innerHTML = lista.map(s =>
            '<li class="d-flex justify-content-between py-1">' + badgeStatus(s.status)
            + '<span>' + numero.format(s.quantidade) + ' <small class="text-muted">(' + decimal.format(s.percentual)
            + '%)</small></span></li>').join('');
    }

    function renderizarRecentes(lista) {
        mensagemCard('card-recentes', lista.length ? '' : 'Nenhum pedido no período selecionado.');
        campo('tabela-recentes').querySelector('tbody').innerHTML = lista.map(p =>
            '<tr>'
            + '<td><a href="/pedidos/' + encodeURIComponent(p.id) + '">#' + escapar(p.id.substring(0, 8)) + '</a></td>'
            + '<td>' + escapar(p.cliente) + '</td>'
            + '<td>' + dataHora.format(new Date(p.dataHoraEntrada)) + '</td>'
            + '<td>' + badgeStatus(p.status) + '</td>'
            + '<td class="text-right">' + moeda.format(p.valorTotal) + '</td>'
            + '</tr>').join('');
    }

    // Produtos mais vendidos
    function renderizarRanking(lista) {
        mensagemCard('card-ranking', lista.length ? '' : 'Nenhum produto vendido no período selecionado.');
        const maior = lista.length ? lista[0].participacao : 0;
        campo('tabela-ranking').querySelector('tbody').innerHTML = lista.map(p =>
            '<tr>'
            + '<td>' + p.posicao + 'º</td>'
            + '<td>' + escapar(p.nome) + '<br><small class="text-muted">' + escapar(p.categoria) + '</small></td>'
            + '<td class="text-right">' + numero.format(p.quantidade) + '</td>'
            + '<td class="text-right">' + moeda.format(p.faturamento) + '</td>'
            + '<td><div class="progress"><div class="progress-bar bg-primary" style="width: '
            + (maior ? (p.participacao / maior * 100) : 0) + '%"></div></div>'
            + '<small>' + decimal.format(p.participacao) + '%</small></td>'
            + '</tr>').join('');
    }

    // Atualização
    const SECOES = [
        { card: 'indicadores', caminho: '/indicadores', renderizar: renderizarIndicadores },
        { card: 'card-vendas', caminho: '/vendas', renderizar: renderizarVendas },
        { card: 'card-status', caminho: '/pedidos/status', renderizar: renderizarStatus },
        { card: 'card-ranking', caminho: '/produtos/mais-vendidos', renderizar: renderizarRanking },
        { card: 'card-categorias', caminho: '/vendas/categorias', renderizar: renderizarCategorias },
        { card: 'card-recentes', caminho: '/pedidos/recentes', renderizar: renderizarRecentes }
    ];

    async function atualizar() {
        const filtros = lerFiltros();
        if (filtros.dataInicio && filtros.dataFim && filtros.dataInicio > filtros.dataFim) {
            toastr.warning('A data inicial não pode ser posterior à data final.');
            return;
        }

        const botao = campo('btn-atualizar');
        botao.disabled = true;

        const resultados = await Promise.allSettled(SECOES.map(async secao => {
            const parametros = Object.assign({}, filtros);
            if (secao.caminho === '/vendas' && campo('agrupamento').value) {
                parametros.agrupamento = campo('agrupamento').value;
            }
            const temOverlay = secao.card !== 'indicadores';
            if (temOverlay) {
                carregando(secao.card, true);
            }
            try {
                secao.renderizar(await buscar(secao.caminho, parametros));
            } catch (e) {
                if (temOverlay) {
                    mensagemCard(secao.card, e.message, true);
                }
                throw e;
            } finally {
                if (temOverlay) {
                    carregando(secao.card, false);
                }
            }
        }));

        botao.disabled = false;
        const erros = [...new Set(resultados.filter(r => r.status === 'rejected').map(r => r.reason.message))];
        erros.forEach(mensagem => toastr.error(mensagem));
        if (!erros.length) {
            campo('ultima-atualizacao').textContent = new Date().toLocaleTimeString('pt-BR');
        }
    }

    function configurarAutoAtualizacao() {
        clearInterval(temporizador);
        if (campo('auto-atualizar').checked) {
            temporizador = setInterval(atualizar, INTERVALO_ATUALIZACAO_MS);
        }
    }

    // Inicialização
    async function iniciar() {
        try {
            const opcoes = await buscar('/filtros');
            preencherSelect('status', opcoes.status, s => STATUS[s].rotulo);
            preencherSelect('categoria', opcoes.categorias);
        } catch (e) {
            toastr.error(e.message);
        }
        periodoPadrao();

        campo('form-filtros').addEventListener('submit', evento => {
            evento.preventDefault();
            atualizar();
        });
        campo('btn-limpar').addEventListener('click', () => {
            campo('form-filtros').reset();
            periodoPadrao();
            atualizar();
        });
        campo('btn-atualizar').addEventListener('click', atualizar);
        campo('auto-atualizar').addEventListener('change', configurarAutoAtualizacao);
        configurarAutoAtualizacao();

        atualizar();
    }

    document.addEventListener('DOMContentLoaded', iniciar);
})();
