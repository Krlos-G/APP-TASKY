# Plano de Implementação — Fatia 4: Hábitos + `streak`

- **Data:** 2026-09-07
- **Projeto:** Tasky — https://github.com/Krlos-G/APP-TASKY
- **Spec de referência:** `docs/superpowers/specs/2026-08-30-app-rotina-pessoal-design.md` (seções 4, 5, 6 e 12)
- **Depende de:** Fatia 3 (concluída, PR #3 mergeado)
- **Status:** aguardando aprovação

---

## Objetivo da fatia

Entra a camada de engajamento. Até aqui o app mostra o dia; agora ele passa a **cobrar e recompensar**
a repetição, que é o motivo pelo qual o projeto existe.

**Critério de pronto:**

1. Cadastro um hábito diário e ele aparece no `Hoje`.
2. Marco `FEITO` três dias seguidos e vejo `streak` 3.
3. `PULADO` não quebra o `streak`; dia devido sem marcação nenhuma quebra.
4. Hábito de dias específicos (`DIAS_SEMANA`) só é cobrado nos dias dele — segunda não quebra o
   `streak` de um hábito de terça e quinta.
5. Desmarcar volta atrás; arquivar tira dos ativos sem perder o histórico.
6. `mvn verify` e os testes do front passam; CI verde no PR.

**Fora do escopo:** agenda `VEZES_POR_SEMANA` (v1.1), histórico em grade estilo "contribuições"
(v1.1), lembrete de hábito no horário (Fatia 7), hábito com meta numérica (pós-MVP), barra de
progresso do dia (Fatia 5, quando houver também tarefas para contar).

---

## Decisões desta fatia

| Decisão | Escolha | Por quê |
|---|---|---|
| Onde vive o `streak` | **Calculado sob demanda, nunca armazenado** | Coluna de `streak` é estado derivado: desatualiza em toda edição de agenda, marcação retroativa e virada de dia. São poucos hábitos e poucos dias — o cálculo é trivial. Já é o que o spec decidiu. |
| Marcar/desmarcar | **`PUT` e `DELETE` em `/habitos/{id}/registros/{data}`** | _Upsert_ idempotente por `(habito, data)`: repetir a chamada não gera 409 do índice único nem duplicata. Casa com o _last-write-wins_ do spec e com a marcação otimista da tela. |
| Contagem do dia corrente | **O dia de hoje ainda em aberto não quebra o `streak`** | Sem isso, o número zeraria toda meia-noite e só voltaria depois da marcação — exatamente o oposto do efeito psicológico pretendido. O `streak` conta de ontem para trás, e soma hoje se hoje já está `FEITO`. |
| `VEZES_POR_SEMANA` | **Recusado com 400 na API** | O enum e o `CHECK` do banco já aceitam, mas `devidoEm` devolve `false` para todo dia. Deixar criar produziria um hábito que nunca aparece no `Hoje` e tem `streak` eternamente 0. |
| Excluir hábito | **Arquivar e excluir são operações distintas** | Arquivar preserva o histórico e congela o `streak`; excluir apaga tudo em cascata. Fazer o `DELETE` "arquivar quando há histórico" seria mágico e imprevisível. A tela avisa o que cada botão faz. |
| Marcação em dia não devido | **Aceita, mas fora do cálculo** | É bônus: não conta pontos e não penaliza. O laço do `streak` só percorre dias devidos, então isso sai de graça. |
| Hábitos no `Hoje` | **Seção própria abaixo da linha do tempo** | Hábito sem `horaPreferida` não tem lugar numa lista cronológica. A seção traz o contador `2/3` e ordena por hora preferida, com os sem hora no fim. |

---

## Etapa 1 — Acesso aos dados e o histórico

`HabitoRepository` e `RegistroHabitoRepository` hoje são interfaces vazias. Ganham as consultas da
fatia, todas **filtradas pelo usuário** — a regra que nasceu na Fatia 3 vale igual aqui, inclusive o
**404 (não 403)** para hábito de outra conta.

- `HabitoRepository`: ativos do usuário ordenados, busca por `(id, usuario)`, contagem de registros.
- `RegistroHabitoRepository`: busca por `(habito, data)`, e a consulta que traz **os registros de
  todos os hábitos do usuário desde uma data** — uma consulta só, para o cálculo do `streak` da
  listagem não virar N+1.
- Marcar exige `Habito` carregado; com `open-in-view: false` o serviço devolve o que já leu, nunca
  uma entidade destacada para o controller navegar.

**Verificação:** teste em que o usuário A tenta ler, marcar e arquivar o hábito do usuário B e recebe
404 nas três tentativas.

---

## Etapa 2 — `StreakCalculator`

A peça de maior risco da fatia, e a única que dá para testar inteira sem banco e sem HTTP.

```java
int calcular(Habito habito, Map<LocalDate, StatusRegistroHabito> registros, LocalDate hoje)
```

**Regras, na ordem em que o laço as aplica:**

1. O ponto de partida é `hoje`, ou a **data de arquivamento** se o hábito estiver arquivado — é isso
   que "congela" o `streak` de um hábito arquivado em vez de deixá-lo cair a zero com o tempo.
2. Se o ponto de partida é hoje, hoje é devido e **ainda não tem marcação**, começa em ontem. O dia
   não acabou; ele ainda não é uma falha.
3. Caminhando para trás: dia **não devido** é pulado, sem contar e sem quebrar. Dia devido `FEITO`
   soma 1. Dia devido `PULADO` não soma, mas **não quebra** — é descanso planejado. Dia devido **sem
   registro** encerra a contagem.
4. O laço para na data de criação do hábito (não faz sentido cobrar dias em que ele não existia) e
   tem teto rígido de 366 iterações, para nenhuma combinação estranha de dados virar laço longo.

**Verificação:** teste unitário _table-driven_ cobrindo, no mínimo: sequência limpa de 3 dias; `PULADO`
no meio; buraco no meio; hábito `DIAS_SEMANA` atravessando dias não devidos; hoje devido e ainda não
marcado (não quebra); hoje devido e já marcado (soma); hábito arquivado (congelado); marcação em dia
não devido (não soma, não quebra); hábito criado ontem (não cobra anteontem).

---

## Etapa 3 — CRUD de hábitos

`HabitoService` e `HabitoController`, sob `/api/v1/habitos`.

| Método | Rota | Observação |
|---|---|---|
| GET | `/habitos` | Ativos, com `streak` e o status de hoje. `?incluirArquivados=true` traz os arquivados |
| POST | `/habitos` | Cria |
| PUT | `/habitos/{id}` | Nome, ícone, cor, agenda, `horaPreferida`, `horaLembrete` |
| PUT | `/habitos/{id}/arquivo` | `{ "arquivado": true }` ou `false` — arquiva e desarquiva |
| DELETE | `/habitos/{id}` | Apaga o hábito **e o histórico**, em cascata |

**Regras:**

- `DIARIO` ignora `diasSemana`; `DIAS_SEMANA` exige **pelo menos um dia** — lista vazia é 400, não um
  hábito que nunca é cobrado.
- `VEZES_POR_SEMANA` → 400 dizendo que fica para depois.
- `@Version` já está na entidade: edição concorrente → 409, como na rotina.
- Editar a agenda vale **só para o futuro** em termos de intenção, mas no MVP o `streak` recalcula
  pela agenda vigente. Está registrado no spec como dívida consciente; a tela não promete o contrário.

**Verificação:** testes de controller para cada rota, incluindo os 400 de agenda incoerente e o 409
de conflito de versão.

---

## Etapa 4 — Marcação e histórico

| Método | Rota | Observação |
|---|---|---|
| PUT | `/habitos/{id}/registros/{data}` | `{ "status": "FEITO" }` ou `"PULADO"` — cria ou atualiza |
| DELETE | `/habitos/{id}/registros/{data}` | Desmarca: apaga a linha |
| GET | `/habitos/{id}/historico?desde=&ate=` | Lista de registros no período |

**Regras:**

- A resposta do `PUT`/`DELETE` devolve **o hábito com o `streak` já recalculado**. Uma ida ao servidor
  em vez de duas, e a tela não precisa saber recalcular nada.
- **Data futura → 400.** "Futuro" é medido no fuso do usuário, não no do servidor.
- Desmarcar um dia sem registro responde **como se tivesse apagado** — idempotente, e o cliente
  otimista pode repetir sem tratar caso especial.
- O `DELETE` do hábito arrasta o histórico pelo `ON DELETE CASCADE` que já existe na V1.

**Sem migração nesta fatia:** `habito` e `registro_habito` nasceram completas na `V1`, com o
`UNIQUE (habito_id, data)` e o índice `(habito_id, data DESC)` que o cálculo do `streak` usa.

**Verificação:** teste marcando o mesmo dia duas vezes (sem duplicar, sem 409), teste de data futura,
teste de desmarcação, teste de `@DataJpaTest` provando que o índice único barra a duplicata no banco.

---

## Etapa 5 — Hábitos no `/dia`

`DiaResponse.habitos` deixa de ser `List<Object>` e passa a ser tipada. Foi para isto que o campo
existia vazio desde a Fatia 3 — a tela `Hoje` não muda de formato, só passa a receber conteúdo.

- Entram os hábitos **devidos naquela data** (e os não devidos que tenham marcação, para o bônus não
  sumir da tela depois de marcado), cada um com `status` do dia e `streak`.
- Ordem: `horaPreferida` crescente, nulos por último, desempate por nome.
- **Dia sem rotina continua mostrando hábitos.** São coisas independentes: não ter modelo atribuído a
  um sábado não significa não ter hábitos no sábado.

**Verificação:** teste de `/dia` em dia sem rotina e com hábito devido — `temRotina: false` e a lista
de hábitos preenchida.

---

## Etapa 6 — Tela `Hábitos`

`HabitoService` no front (`core/habitos/`), modelos tipados e a tela que hoje é um _placeholder_.

- Lista com nome, agenda por extenso ("Todo dia", "Ter, Qui"), `streak` e o botão de marcar hoje.
- Formulário de criação/edição: nome, tipo de agenda, seletor de dias quando `DIAS_SEMANA`, hora
  preferida, cor.
- Arquivar/desarquivar, e excluir **com confirmação que diz explicitamente que o histórico vai junto**.
- Histórico do hábito em lista simples: últimos 30 dias, `FEITO`/`PULADO`/vazio.

**Verificação:** testes de componente com HTTP mockado, e verificação no navegador.

---

## Etapa 7 — Hábitos na tela `Hoje`

Seção "Hábitos de hoje" abaixo da linha do tempo, com contador `2/3`.

- Marcar é **otimista com rollback**: o item muda na hora e volta ao estado anterior se o servidor
  recusar, com aviso. É a única escrita otimista do app — as outras são pessimistas, conforme o spec.
- `streak` visível no item, atualizado com a resposta do servidor.
- Pular fica num controle secundário (não é a ação principal; a principal é fazer).

**Verificação:** teste de rollback (servidor devolve erro → o item volta ao estado anterior e a
mensagem aparece), e verificação no navegador marcando, desmarcando e pulando.

---

## Etapa 8 — Fechamento

- **Teste de fluxo** (`FluxoHabitosTests`), o critério de pronto virando teste: criar hábito diário →
  marcar em três dias consecutivos, viajando no tempo com o `RelogioAjustavel` → `GET /habitos`
  devolve `streak` 3 → desmarcar o dia do meio → `streak` cai para 1.
- Verificação no navegador do fluxo completo.
- PR com CI verde.

---

## Ordem e pontos de parada

```
1. Acesso aos dados ──────── teste de isolamento entre usuarios passa
2. StreakCalculator ──────── a regra do streak esta provada sem banco
3. CRUD de habitos ───────── consigo cadastrar habitos via HTTP
4. Marcacao e historico ──── marcar/desmarcar responde com o streak certo
5. Habitos no /dia ───────── o contrato do dia para de vir vazio
6. Tela Habitos ─────────── ponto de parada: cadastro habitos pela interface
7. Habitos no Hoje ──────── ponto de parada: marco o habito e vejo o streak subir
8. Fechamento ───────────── FATIA 4 CONCLUIDA
```

**Branch:** `feature/fatia-4-habitos`, a partir de `main` atualizada.

---

## Riscos

| Risco | Contenção |
|---|---|
| `streak` zerando na virada do dia | A regra do "dia em aberto" é decisão explícita da Etapa 2 e tem teste próprio, com o relógio parado às 00:05. |
| Fuso do usuário na data do registro | A data é validada contra o "hoje" do usuário (`Clock` + `ZoneId`), nunca contra o relógio do servidor. Testes em fuso negativo às 23h e 01h. |
| N+1 ao listar hábitos com `streak` | Uma consulta traz os registros de todos os hábitos do período; o cálculo acontece em memória. |
| `LazyInitializationException` de novo | Já aconteceu três vezes com `open-in-view: false`. O serviço devolve DTO montado dentro da transação, e o teste de controller (não só o de serviço) cobre cada rota nova — inclusive as de edição, que foi exatamente o buraco da Fatia 3. |
| Marcação otimista dessincronizar a tela | O servidor devolve o hábito completo com o `streak`; a resposta substitui o estado otimista em vez de somar a ele. |
| Relógio compartilhado entre testes | O `@BeforeEach` redefine o `RelogioAjustavel`, e avançar o relógio expira o token — usar o `agoraE(instante)`, que reemite. |

---

## Próxima fatia

**Fatia 5 — Tarefas:** CRUD de tarefas, encaixe no dia, seção "Atrasadas" e a barra de progresso do
`Hoje`, que só faz sentido quando há hábitos **e** tarefas para contar.
