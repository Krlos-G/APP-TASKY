# Plano de Implementação — Fatia 3: Rotina e tela Hoje

- **Data:** 2026-09-06
- **Projeto:** Tasky — https://github.com/Krlos-G/APP-TASKY
- **Spec de referência:** `docs/superpowers/specs/2026-08-30-app-rotina-pessoal-design.md` (seções 5 e 6)
- **Depende de:** Fatia 2 (concluída, PR #2 mergeado)
- **Status:** aguardando execução

---

## Objetivo da fatia

Sair do app vazio: montar a agenda-modelo do dia e ver a linha do tempo montada a partir dela.
É a primeira fatia com dados de domínio de verdade.

**Critério de pronto:**

1. Consigo criar um `ModeloDia` ("Dia útil") com vários blocos de horário.
2. Consigo atribuir modelos aos sete dias da semana.
3. A tela `Hoje` mostra a linha do tempo do dia certo, no meu fuso, com o bloco atual destacado.
4. Sem rotina atribuída ao dia, a tela explica isso e oferece o atalho para criá-la.
5. `mvn verify` e os testes do front passam; CI verde no PR.

**Fora do escopo:** hábitos (Fatia 4), tarefas (Fatia 5), check-off de bloco, edição visual
arrastável, overrides de um dia específico.

---

## Decisões desta fatia

| Decisão | Escolha | Por quê |
|---|---|---|
| Edição dos blocos | **Formulário** (horário digitado) | Destrava a tela `Hoje` rápido. A grade arrastável fica como melhoria futura: dá bem mais trabalho e arrastar em tela de celular é impreciso. A rotina é montada uma vez e quase não se mexe depois. |
| Rota do dia | **`GET /api/v1/dia?data=`** | Consistente com a convenção do projeto — domínio em português, técnico em inglês. Diverge do texto do spec, que dizia `/day?date=`. |
| Aviso de sobreposição | **Calculado no servidor** | Fonte única da verdade; o front só exibe. |
| `PingController` | **Removido** | Existia só para exercitar a segurança enquanto não havia rotas de domínio. Agora há. |

---

## Etapa 1 — Acesso aos dados por usuário

Esta é a primeira fatia em que os dados pertencem a alguém, então a regra de propriedade nasce aqui
e vale para todas as próximas.

- Consultas dos repositórios **sempre filtram pelo usuário autenticado**. Nunca `findById` puro em
  entidade de domínio.
- Recurso de outro usuário responde **404, não 403**: dizer "existe, mas não é seu" já entrega
  informação a quem sonda.
- Um componente pequeno resolve o `Usuario` a partir do `UsuarioAutenticado` do `SecurityContext`,
  para os serviços não repetirem esse passo.
- `ObjectOptimisticLockingFailureException` passa a ser tratada no `ApiExceptionHandler` como
  **409**, com mensagem pedindo para recarregar. É o `@Version` das entidades finalmente em uso.

**Verificação:** teste em que o usuário A tenta ler e alterar o modelo do usuário B e recebe 404 nos
dois casos.

---

## Etapa 2 — CRUD da rotina

`RotinaService` e `RotinaController`, sob `/api/v1/rotina`.

| Método | Rota | Observação |
|---|---|---|
| GET | `/modelos` | Lista os modelos com seus blocos |
| POST | `/modelos` | Cria modelo vazio |
| PUT | `/modelos/{id}` | Renomeia, marca como padrão |
| DELETE | `/modelos/{id}` | **Bloqueado (409) se estiver atribuído a algum dia** |
| POST | `/modelos/{id}/blocos` | Adiciona bloco |
| PUT | `/blocos/{id}` | Edita título, horário, cor, antecedência do lembrete |
| DELETE | `/blocos/{id}` | Remove |
| GET | `/semana` | Mapa Seg→Dom com o modelo de cada dia |
| PUT | `/semana` | Atribui modelos aos dias, em uma operação |

**Regras:**

- `horaFim > horaInicio` validado na aplicação **e** no banco (`CHECK` da V1). Bloco cruzando a
  meia-noite não é suportado no MVP.
- **Sobreposição é permitida, com aviso.** Às vezes é intencional (um bloco de foco dentro do
  expediente). A resposta do modelo traz uma lista de avisos identificando os pares que se
  sobrepõem; o front exibe, mas não impede.
- Atribuir a semana é **substituição do conjunto**, não incremento: o cliente manda os sete dias e o
  servidor reconcilia. Evita estado meio-atualizado se uma chamada falhar no meio.

**Verificação:** criar modelo com blocos, atribuir à semana, tentar apagar modelo em uso (409),
criar bloco com `horaFim` antes de `horaInicio` (400), criar blocos sobrepostos (201 + aviso).

---

## Etapa 3 — Montagem do dia

`DiaAssembler` + `GET /api/v1/dia?data=`. O coração da fatia.

- Sem o parâmetro `data`, o servidor usa **hoje no fuso do usuário** — nunca no fuso do servidor.
- Resolve o dia da semana daquela data no fuso do usuário, encontra o `AtribuicaoDia` e devolve os
  blocos do modelo correspondente, ordenados por horário.
- Sem atribuição para o dia, devolve **200 com a lista vazia** e uma marca indicando ausência de
  rotina. Não é erro: é um estado legítimo que a tela precisa saber representar.
- O formato da resposta já prevê os campos de hábitos e tarefas (listas vazias por enquanto), para
  que as Fatias 4 e 5 não precisem quebrar o contrato.

**Testes com `Clock` fixo:** o dia da semana correto perto da meia-noite em fusos diferentes;
data explícita no passado e no futuro; dia sem rotina; blocos ordenados.

---

## Etapa 4 — Camada de dados no front

- Tipos TypeScript espelhando os DTOs.
- `RotinaService` e `DiaService` (Angular) consumindo os endpoints.
- **`TimeProvider`**: relógio injetável no front, previsto no spec e ainda não criado. A tela `Hoje`
  precisa de "agora" e os testes precisam controlá-lo — sem isso, testar o bloco atual exigiria
  esperar o relógio real virar.

**Verificação:** testes dos serviços com `HttpTestingController`.

---

## Etapa 5 — Tela Rotina

Rota `/rotina`, protegida. Acessível pelo `Resumo` e pelo estado vazio do `Hoje`.

- Lista de modelos; criar, renomear e apagar.
- Dentro do modelo: lista de blocos com formulário de horário (`<input type="time">`), título e cor;
  adicionar, editar e remover.
- Grade Seg→Dom para atribuir o modelo de cada dia.
- Avisos de sobreposição exibidos junto dos blocos envolvidos.
- Erro 409 ao apagar modelo em uso vira mensagem explicando em quais dias ele está.

**Verificação manual:** montar do zero um "Dia útil" com quatro blocos e atribuí-lo a seg–sex.

---

## Etapa 6 — Tela Hoje

- Linha do tempo dos blocos do dia, em ordem.
- **Bloco "agora"** destacado, com quanto falta para terminar e qual vem em seguida. Atualiza a cada
  minuto, via `TimeProvider`.
- Antes do primeiro bloco ou depois do último, mostra o próximo (ou encerra o dia) em vez de
  destacar nada.
- Estado vazio: "sem rotina para hoje" com atalho para `/rotina`.
- Cabeçalho com a data por extenso.

**Verificação:** no navegador, com o relógio controlado nos testes e olho vivo no caso de virada de
dia.

---

## Etapa 7 — Fechamento

- Teste de fluxo: criar modelo → adicionar blocos → atribuir à semana → `GET /dia` devolve a linha
  do tempo esperada.
- Remover o `PingController` e o teste que dependia dele (o `SecurityConfigTest` usa `/api/v1/ping`
  para exercitar rota protegida — passa a usar uma rota real da rotina).
- Verificação no navegador do fluxo completo.
- PR com CI verde.

---

## Ordem e pontos de parada

```
1. Acesso por usuario ────── teste de isolamento entre usuarios passa
2. CRUD da rotina ────────── consigo montar a rotina via HTTP
3. Montagem do dia ───────── GET /dia devolve a linha do tempo correta
4. Camada de dados no front  testes dos servicos verdes
5. Tela Rotina ───────────── ponto de parada: monto a rotina pela interface
6. Tela Hoje ─────────────── ponto de parada: vejo o dia e o bloco atual
7. Fechamento ────────────── FATIA 3 CONCLUIDA
```

**Branch:** `feature/fatia-3-rotina`.

---

## Riscos

| Risco | Contenção |
|---|---|
| Fuso horário errado na virada do dia | Todo cálculo passa pelo `Clock` e pelo `ZoneId` do usuário; testes cobrem 23h e 01h em fusos diferentes. |
| Contrato do `/dia` mudar nas Fatias 4 e 5 | O formato já reserva os campos de hábitos e tarefas, mesmo vazios. |
| Regra de propriedade esquecida em algum endpoint novo | A regra nasce em um único ponto na Etapa 1, e o teste de isolamento cobre cada recurso. |
| Formulário de horário inconsistente entre navegadores | `<input type="time">` tem suporte amplo; o valor é sempre `HH:mm`, e o servidor valida de novo. |

---

## Próxima fatia

**Fatia 4 — Hábitos + `streak`:** CRUD de hábitos, marcação diária, `StreakCalculator` e a
aparição dos hábitos na linha do tempo do `Hoje`.
