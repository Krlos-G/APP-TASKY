# Plano de Implementação — Fatia 5: Tarefas

- **Data:** 2026-09-16
- **Projeto:** Tasky — https://github.com/Krlos-G/APP-TASKY
- **Spec de referência:** `docs/superpowers/specs/2026-08-30-app-rotina-pessoal-design.md` (seções 4, 6 e 12)
- **Depende de:** Fatia 4 (concluída, PR #4 mergeado)
- **Status:** aguardando aprovação

---

## Objetivo da fatia

Terceira peça do dia: as tarefas avulsas. Com elas, a tela `Hoje` passa a ter tudo o que precisa
contar, e a barra de progresso finalmente faz sentido.

**Critério de pronto** (o do spec): *crio tarefa pelo `[ + ]`, encaixo no dia, concluo.*

1. Crio uma tarefa pela tela `Nova tarefa`, aberta pelo `[ + ]` do `Hoje` ou de `Tarefas`.
2. Tarefa com data e horário aparece no `Hoje` daquele dia; sem horário, aparece também, no fim.
3. Concluo e desfaço com um toque, e a barra de progresso acompanha.
4. Tarefa de ontem não feita aparece em "Atrasadas" no topo do `Hoje`.
5. A tela `Tarefas` lista por filtro.
6. `mvn verify` e os testes do front passam; CI verde no PR.

**Fora do escopo** — já fora do MVP no spec, ou simplesmente não pedido:
- `bloco_planejado_id` (a coluna existe, mas o spec não a lista nos campos da tela)
- Status `CANCELADA` (o enum aceita; nenhuma tela usa)
- Sugestão automática de encaixe e aviso de "dia lotado" (v1.1 no spec)
- Ação rápida de "adiar" ou "trazer para hoje" — editar a data resolve
- `hora_lembrete` no formulário: a API aceita, mas o campo só entra na tela na Fatia 7, quando
  lembrete existir de verdade (mesma escolha feita nos hábitos)

---

## Decisões desta fatia

| Decisão | Escolha | Por quê |
|---|---|---|
| **Filtro "Próximas"** ⚠️ | **Acrescentar** aos quatro do spec | O spec lista Hoje · Atrasadas · Sem data · Concluídas. Tarefa planejada para amanhã não cai em nenhum deles e ficaria invisível na tela `Tarefas`. |
| **Tarefas no `Hoje`** ⚠️ | **Seção própria**, como os hábitos | O spec pedia intercalar com os blocos da linha do tempo. Mas bloco tem início e fim, tarefa tem um horário só, e tarefa sem horário não tem lugar numa lista cronológica. É o mesmo raciocínio que você aprovou na Fatia 4. |
| Barra de progresso | Hábitos do dia + tarefas do dia; **atrasadas fora da conta** | Atrasada é dívida de outro dia. Contá-la faria o dia de hoje nascer "atrasado" no progresso. Calculada no front, a partir do que já está na tela. |
| Concluir/desfazer | `PUT` e `DELETE` em `/tarefas/{id}/conclusao` | Mesmo formato da marcação de hábito: idempotente, devolve a tarefa atualizada. |
| `atrasada` e `vencida` | **Calculadas no servidor** e enviadas na resposta | Dependem de "hoje no fuso da conta", que só o servidor sabe. Mesmo motivo do `devidoHoje` nos hábitos. |
| Horário sem data | **400** | Horário sem dia não significa nada. No formulário, o campo de horário só habilita com a data preenchida. |

As duas marcadas com ⚠️ divergem do spec e precisam do seu ok.

---

## Etapa 1 — Consultas por filtro

`TarefaRepository` ganha as consultas, todas filtradas pelo dono (404 para tarefa de outra conta).

| Filtro | Regra |
|---|---|
| `HOJE` | `data_planejada = hoje`, a fazer **e** feitas (feita continua visível, marcada) |
| `PROXIMAS` | `data_planejada > hoje`, a fazer |
| `ATRASADAS` | `data_planejada < hoje`, a fazer |
| `SEM_DATA` | `data_planejada` nula, a fazer |
| `CONCLUIDAS` | feitas, da mais recente para a mais antiga |

Ordem nas listas de pendentes: horário (sem horário no fim), depois prioridade (alta primeiro),
depois título. A prioridade é ordenada em memória — ordenar o enum no SQL daria ordem alfabética
(ALTA, BAIXA, MEDIA).

**Verificação:** teste de repositório com o isolamento entre contas e a fronteira de cada filtro
(tarefa de ontem é atrasada, não é de hoje; feita não é atrasada).

---

## Etapa 2 — CRUD e conclusão

`TarefaService` e `TarefaController`, sob `/api/v1/tarefas`.

| Método | Rota | Observação |
|---|---|---|
| GET | `/tarefas?filtro=` | Padrão `HOJE` |
| GET | `/tarefas/{id}` | Para a tela de edição |
| POST | `/tarefas` | Cria |
| PUT | `/tarefas/{id}` | Edita todos os campos |
| DELETE | `/tarefas/{id}` | Apaga |
| PUT | `/tarefas/{id}/conclusao` | Marca feita, grava `concluido_em` |
| DELETE | `/tarefas/{id}/conclusao` | Volta a fazer, limpa `concluido_em` |

`TarefaResponse` traz os campos da tarefa mais `atrasada` e `vencida`.

**Verificação:** testes de controller por rota, incluindo o 400 de horário sem data, concluir duas
vezes sem erro, e o 404 entre contas em leitura, edição, conclusão e exclusão.

---

## Etapa 3 — Tarefas no `/dia`

O campo `tarefas` do `DiaResponse` deixa de ser `List<Object>` — o último espaço reservado desde a
Fatia 3. Entra também um campo novo, `atrasadas`.

- `tarefas`: as do filtro `HOJE` para a data pedida.
- `atrasadas`: só preenchida **quando a data pedida é hoje**. Olhando um dia passado, "atrasada em
  relação a quê" não tem resposta útil.

Campo novo não quebra o contrato: o front antigo ignora o que não conhece.

**Verificação:** teste de `/dia` com tarefa de hoje, de ontem (vai para `atrasadas`) e feita ontem
(não aparece em lugar nenhum).

---

## Etapa 4 — Tela `Tarefas`

`TarefaService` no front (`core/tarefas/`), modelos tipados e a tela que hoje é um _placeholder_.

- Filtros em abas: Hoje · Próximas · Atrasadas · Sem data · Concluídas.
- Cada item: caixa de concluir, título, data/horário, prioridade, e o selo vermelho quando `vencida`.
- Tocar no item abre a edição. Botão `[ + ]` leva para `Nova tarefa`.

**Verificação:** testes de componente e verificação no navegador.

---

## Etapa 5 — Tela `Nova tarefa`

Rota própria, **não modal**, como pede o spec: `/tarefas/nova` para criar, `/tarefas/:id` para
editar a mesma tela.

- Campos: título, observações, prioridade, estimativa em minutos, data planejada, horário, prazo.
- Aberta pelo `[ + ]` do `Hoje`, já vem com a data de hoje preenchida (`?data=`).
- Na edição, botão de excluir com confirmação.
- Salvar volta para a tela de onde veio.

**Verificação:** testes de componente (validação, pré-preenchimento, volta para a origem) e
verificação no navegador.

---

## Etapa 6 — Tarefas no `Hoje`

- Seção **"Atrasadas"** no topo, só quando houver.
- Seção **"Tarefas de hoje"** junto da de hábitos, com os horários visíveis.
- **Barra fina de progresso** no cabeçalho: feitos sobre total, somando hábitos e tarefas do dia.
- Concluir é **otimista com rollback**, reaproveitando o padrão dos hábitos.
- Botão `[ + ]` flutuante.

**Verificação:** teste de rollback, teste da conta da barra (atrasada fora), verificação no navegador
em desktop e no tamanho do iPhone.

---

## Etapa 7 — Fechamento

- **Teste de fluxo** (`FluxoTarefasTests`): criar tarefa para hoje com horário → aparece no `/dia` →
  concluir → avança o relógio → tarefa de ontem **não feita** aparece em `atrasadas`; a feita não.
- Verificação final no navegador.
- PR com CI verde.

---

## Ordem e pontos de parada

```
1. Consultas por filtro ───── fronteira de cada filtro provada no banco
2. CRUD e conclusao ──────── consigo criar e concluir tarefa via HTTP
3. Tarefas no /dia ───────── o ultimo campo vazio do contrato se preenche
4. Tela Tarefas ──────────── ponto de parada: vejo o backlog por filtro
5. Tela Nova tarefa ──────── ponto de parada: crio e edito pela interface
6. Tarefas no Hoje ──────── ponto de parada: concluo e a barra enche
7. Fechamento ───────────── FATIA 5 CONCLUIDA
```

**Branch:** `feature/etapa5-tarefas`, a partir de `main` atualizada.

---

## Riscos

| Risco | Contenção |
|---|---|
| "Hoje" errado na virada do dia define o que é atrasada | Tudo passa pelo `DataDoUsuario`; teste de fluxo avança o relógio de verdade. |
| `LazyInitializationException` (já foram três) | `Tarefa` só tem `usuario` preguiçoso e a resposta não o usa; ainda assim, teste de controller em toda rota, inclusive edição. |
| Voltar da `Nova tarefa` para a tela errada | A origem vai na navegação e tem teste próprio. |
| Barra de progresso divergir do que se vê | Calculada a partir das mesmas listas que a tela renderiza, não de um número à parte. |

---

## Próxima fatia

**Fatia 6 — Resumo:** progresso do dia e visão da semana. Depois dela, a fatia de design.
