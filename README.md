# 🍕 Pediu Patrão — Dashboard Administrativo

Sistema de pedidos para lanchonete/pizzaria, com um **Dashboard Administrativo** que consome uma **API REST**
própria. Com ele, o responsável pelo estabelecimento acompanha as vendas, a situação dos pedidos e as tendências da operação.

**Tecnologias:** Java 21 · Spring Boot 3.4 · Spring Security · MongoDB · Thymeleaf · Bootstrap 5 · Chart.js · Grafana · Docker · JUnit 5 · Testcontainers

| | |
|---|---|
| 🎬 [Vídeo do dashboard](#-vídeo-do-dashboard) | 📋 [Decumentação Completa](#1-Documentacao) |

---

## 🎬 Vídeo do dashboard

Demonstração do Dashboard Administrativo

*DePedidos de uma pizzaria de médio porte: login, indicadores, gráficos,
ranking, últimos pedidos, filtros, validação, a API REST e o mesmo painel no Grafana.*
🎬 [Vídeo do dashboard](#-vídeo-do-dashboard)

---

## 1. Requisitos funcionais e não funcionais


### Requisitos funcionais

| ID | Requisito | Descriçãoo |
|---|---|---|
| **RF01** | Indicadores gerais de vendas | Faturamento, pedidos válidos, ticket médio, cancelamentos (qtd. e %), itens vendidos, descontos, tempo médio de atendimento, horário de pico, produto campeão e **variação em relação ao período anterior** |
| **RF02** | Análise de vendas (gráficos) | Evolução de faturamento × pedidos (por dia, semana ou mês), pedidos por hora do dia e faturamento por categoria |
| **RF03** | Situação dos pedidos | Gráfico com os 7 status do fluxo e tabela dos últimos pedidos |
| **RF04** | Produtos mais vendidos | Ranking em gráfico e tabela (quantidade e receita) |
| **RF05** | Filtros de consulta | Período (com atalhos), status, categoria e agrupamento; os filtros ficam na URL para compartilhar |
| **RF06** | Integração com a API | 8 endpoints REST; a tela e o Grafana consomem a mesma API |
| **RF07** | Atualização e tratamento de dados | Atualização automática a cada 60 s, botão Atualizar, indicador de carregando, mensagens de erro, validação na tela e na API |

### Requisitos não funcionais (resumo)

| Categoria | Destaques |
|---|---|
| **Segurança** | Acesso só para GERENTE e ADMIN, verificado em **duas camadas** (rota + `@PreAuthorize`); API somente leitura; chave `X-API-KEY` exclusiva para o Grafana; DTOs em vez de entidades; proteção contra XSS e CSRF; acessos negados auditados |
| **Desempenho** | Índice em `dataCriacao` + projeção de campos: consulta de 8 meses (~13 mil pedidos) caiu de **4,5 s para 1,1 s** |
| **Confiabilidade** | Valores em `BigDecimal` com arredondamento comercial; nenhuma divisão por zero |
| **Manutenibilidade** | Regras de cálculo numa classe pura (`DashboardCalculadora`), arquitetura em camadas, testes automatizados |
| **Usabilidade** | Português, R$ e datas no formato brasileiro, layout responsivo, mensagens claras |

---

## 2. Documentação dos endpoints


**URL base:** `http://localhost:8080/api/dashboard` · **Métodos:** somente `GET` · **Formato:** JSON

| Endpoint | Retorna | Requisito |
|---|---|---|
| `GET /resumo` | Indicadores gerais + comparação com o período anterior | RF01 |
| `GET /vendas?agrupamento=DIA\|SEMANA\|MES` | Série de faturamento e pedidos | RF02 |
| `GET /vendas-por-hora` | 24 posições (0h–23h) | RF02 |
| `GET /vendas-por-categoria` | Faturamento e unidades por categoria | RF02 |
| `GET /status` | Quantidade e % por status | RF03 |
| `GET /pedidos-recentes?limite=10` | Últimos pedidos | RF03 |
| `GET /produtos-mais-vendidos?limite=5` | Ranking de produtos | RF04 |
| `GET /filtros` | Opções para montar os filtros | RF05 |

**Parâmetros comuns (opcionais):** `inicio` e `fim` (`AAAA-MM-DD`, padrão últimos 30 dias, máx. 366 dias),
`status` e `categoria`.

**Autenticação:** sessão do sistema (GERENTE/ADMIN) **ou** cabeçalho `X-API-KEY` (Grafana).

### Exemplo

```bash
curl -H "X-API-KEY: <chave>" "http://localhost:8080/api/dashboard/resumo?inicio=2026-11-01&fim=2026-11-07"
```

```json
{
  "filtro": { "inicio": "2026-11-01", "fim": "2026-11-07", "status": null, "categoria": null },
  "faturamento": 30510.59,
  "totalPedidos": 362,
  "pedidosValidos": 347,
  "pedidosCancelados": 15,
  "ticketMedio": 87.93,
  "taxaCancelamento": 4.1,
  "itensVendidos": 1375,
  "totalDescontos": 144.41,
  "tempoMedioSaidaMinutos": 34.2,
  "comparativo": { "faturamentoAnterior": 33437.21, "variacaoFaturamento": -8.8, "variacaoPedidos": -10.8, "...": "..." }
}
```

### Possíveis erros

Formato padrão: `{"erro": "mensagem"}`

| Código | Quando | Exemplo de mensagem |
|---|---|---|
| `400` | Data inicial depois da final | `A data inicial não pode ser posterior à data final.` |
| `400` | Período maior que 366 dias | `O período máximo de consulta é de 366 dias.` |
| `400` | Data em formato errado, `limite` não numérico | `Requisição inválida: verifique os parâmetros e o corpo enviados.` |
| `400` | Status / categoria / agrupamento inexistente | `Status inválido: ENTREGANDO` |
| `400` | `limite` fora de 1–50 | `O limite deve estar entre 1 e 50.` |
| `401` | Sem login, ou chave ausente ou errada | `Autenticação necessária.` |
| `403` | Perfil sem permissão (ATENDENTE) | `Você não tem permissão para esta ação.` |
| `403` | Tentativa de escrita (`POST`/`PUT`/`DELETE`) | `Token CSRF ausente ou inválido...` |

---

## 3. Evidências de testes


```
[INFO] Tests run: 143, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```


| Tipo | Ferramentas | Destaques |
|---|---|---|
| **Integração (ponta a ponta)** | `@SpringBootTest` + `TestRestTemplate` + **Testcontainers (MongoDB 7 real em Docker)** | 23 cenários: indicadores calculados a partir do banco, limites do período (23:59:59 entra, 00:00 do dia seguinte não), filtros, dados antigos, erros 400/401, API somente leitura, índice criado |
| **Segurança** | `@WebMvcTest` + MockMvc + `SecurityConfig` real | Perfis, chave do Grafana, CSRF, sequência de bloqueio do login |
| **Unitários** | JUnit 5 + Mockito | Regras de cálculo, validação de filtros, gerador de dados, recuperação de senha |
| **Manuais** | Navegador, `curl`, Grafana | Os 8 endpoints no banco real, todos os erros, a tela, o Grafana e a medição de desempenho |

---



## Como executar

**Pré-requisitos:** Java 21 e acesso ao MongoDB (Atlas ou local). O Docker é opcional, usado no Grafana e nos testes de integração.

```bash
./mvnw spring-boot:run
```

Acesse `http://localhost:8080` e entre com `gerente` / `teste123` (usuário de demonstração; troque a senha).
No menu, clique em **Dashboard**.

### Variáveis de ambiente

| Variável | Descrição |
|---|---|
| `SPRING_DATA_MONGODB_URI` / `SPRING_DATA_MONGODB_DATABASE` | Conexão com o banco (substituem os valores do `application.properties`) |
| `DASHBOARD_API_KEY` | Chave que o Grafana usa para ler a API (a mesma do `grafana/.env`) |
| `SMTP_HOST`, `SMTP_USUARIO`, `SMTP_SENHA` | Envio de e-mail do "Esqueci a senha" (vazio = o link aparece no log) |
| `URL_BASE` | Endereço do sistema usado nos links de e-mail |

### Grafana

```bash
cd grafana && docker compose up -d
```

Abra `http://localhost:3000` (`admin` / `admin`). O painel já vem configurado.

### Testes

```bash
./mvnw clean test
```

Os testes de integração precisam do Docker aberto.

---


## Estrutura do projeto

```
src/main/java/com/umc/pediupatrao/
├── controller/api/DashboardApiController.java   # endpoints REST do dashboard
├── service/DashboardService.java                # filtros, acesso aos dados, segurança
├── service/DashboardCalculadora.java            # regras dos indicadores (classe pura)
├── dto/DashboardDto.java                        # formato das respostas JSON
├── security/DashboardApiKeyFilter.java          # chave X-API-KEY do Grafana
src/main/resources/
├── templates/dashboard.html                     # tela do dashboard
└── static/js/dashboard.js                       # consumo da API e gráficos (Chart.js)
grafana/                                         # docker-compose + painel provisionado
docs/                                            # documentação, capturas e vídeo
```
