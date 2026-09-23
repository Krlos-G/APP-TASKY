# Plano de Implementação — Fatia 6: Resumo

- **Data:** 2026-09-23
- **Projeto:** Tasky — https://github.com/Krlos-G/APP-TASKY
- **Spec de referência:** `docs/superpowers/specs/2026-08-30-app-rotina-pessoal-design.md` (seções 6 e 12)
- **Depende de:** Fatia 5 (concluída, PR #5 mergeado)
- **Status:** aguardando aprovação

---

## Objetivo da fatia

Até aqui o app mostra o **agora**. Esta fatia mostra o **acumulado**: como foi o dia e como está
indo a semana. É a última fatia funcional antes da de design.

**Critério de pronto** (o do spec): *dashboard dia + semana funcionando.*

1. Vejo num lugar só quanto do dia já saiu do caminho.
2. Vejo a semana: hábitos cumpridos sobre cobrados, tarefas concluídas, e os `streak` de cada hábito.
3. Daqui chego à `Rotina`.
4. Mudo o fuso do sistema e o app passa a tratar o dia certo, sem eu configurar nada.
5. `mvn verify` e os testes do front passam; CI verde no PR.

**Fora do escopo:** gráficos e alternância semana/mês (o spec põe os dois no futuro), comparação
com semanas anteriores, e qualquer métrica nova que não seja contagem do que já existe.

---

## Decisões desta fatia

| Decisão | Escolha | Por quê |
|---|---|---|
| **Fuso horário** | **Vem do aparelho, sem tela** | O spec previa um ajuste manual, mas ninguém deveria precisar escolher isso à mão. O navegador já sabe o fuso; o app manda ao abrir e o servidor grava quando muda. O fuso **precisa ficar gravado** porque o disparo de lembretes da Fatia 7 roda em segundo plano, sem requisição para consultar o aparelho. |
| A semana conta **de segunda até hoje** | Não a semana inteira | Numa quarta, contar os sete dias mostraria "6/21" e pareceria fracasso quando na verdade faltam quatro dias. O que já foi cobrado é o que conta. |
| Semana começa na **segunda** | — | É a convenção daqui, e o `DiaSemana` do projeto já começa em SEG. |
| Sem gráfico | Números e lista | O spec põe gráficos no futuro. Três números honestos e a lista de `streak` já respondem "como estou indo". |
| Um endpoint só | `GET /api/v1/resumo` | A tela faz uma pergunta só; duas chamadas para montar um painel seriam duas chances de mostrar números de momentos diferentes. |

**Consequência que vale saber:** viajar passa a mudar o que é "hoje" para o app, e um voo para o
leste pode encurtar um dia a ponto de quebrar um `streak`. É o comportamento certo para quem está
vivendo naquele fuso, e é o que a maioria dos apps faz — mas é automático, não uma escolha sua.

---

## Etapa 1 — Cálculo e endpoint do resumo

`ResumoService` e `ResumoController`, sob `/api/v1/resumo`. Nada de tabela nova: é tudo contagem
do que já está gravado.

A resposta, em três partes:

- **`dia`** — hábitos feitos sobre devidos hoje, tarefas feitas sobre as de hoje, e quantas
  atrasadas existem. São os mesmos números que a tela `Hoje` mostra, vindos do mesmo serviço.
- **`semana`** — o período (segunda até hoje), hábitos cumpridos sobre cobrados no período,
  e tarefas concluídas no período.
- **`streaks`** — os hábitos ativos com o `streak` de cada um, do maior para o menor.

**Regras:**

- "Cumprido" na semana é `FEITO`. `PULADO` não conta como feito **nem como cobrado** — descanso
  planejado não deveria puxar a média para baixo.
- Tarefa concluída na semana usa `concluido_em`, convertido para o fuso da conta.
- Tudo parte do `DataDoUsuario`, como no resto do app.

**Verificação:** testes de controller com relógio fixo numa quarta-feira, cobrindo a fronteira da
semana (o domingo anterior não entra), o `PULADO` fora das duas contas, e o 404/401 de praxe.

---

## Etapa 2 — Tela `Resumo`

O dashboard, no lugar do _placeholder_ atual.

- **Hoje:** a barra de progresso e os números do dia.
- **Semana:** o período por extenso, hábitos X/Y, tarefas concluídas.
- **Sequências:** a lista de hábitos com o `streak`, maior primeiro. Sem hábito nenhum, um convite
  para criar o primeiro.
- O atalho para a `Rotina`, que já existe no _placeholder_.

**Verificação:** testes de componente e verificação no navegador.

---

## Etapa 3 — Fuso horário do aparelho

Sem tela nenhuma.

- `PUT /api/v1/usuarios/eu/fuso` grava o fuso. Valor desconhecido responde 400 — o `ApiException`
  já tem essa mensagem desde a Fatia 1.
- O front manda `Intl.DateTimeFormat().resolvedOptions().timeZone` **uma vez por sessão**, logo
  depois de a sessão ficar de pé.
- Servidor grava só quando o valor **mudou**: reabrir o app no mesmo lugar não escreve nada.
- Falha aqui é **silenciosa**. É sincronização de fundo; um erro não pode virar um aviso vermelho
  numa tela que o usuário abriu para ver outra coisa.

**Verificação:** teste de controller para a troca e para o fuso inválido; um teste provando que o
`/dia` passa a responder outro dia depois da troca — é o efeito que dá sentido a tudo isso; e um
teste do front garantindo que o envio acontece uma vez só e que a falha não aparece na tela.

---

## Etapa 4 — Fechamento

- **Teste de fluxo** (`FluxoResumoTests`): marcar hábito e concluir tarefa → o resumo do dia
  acompanha → avançar para o dia seguinte → o número do dia zera, mas o da semana mantém o que
  passou.
- Verificação final no navegador.
- PR com CI verde.

---

## Ordem e pontos de parada

```
1. Calculo e endpoint ───── GET /resumo devolve os numeros certos
2. Tela Resumo ─────────── ponto de parada: vejo o painel do dia e da semana
3. Fuso do aparelho ────── mudo o fuso do sistema e o app acompanha sozinho
4. Fechamento ──────────── FATIA 6 CONCLUIDA
```

**Branch:** `feature/etapa6-resumo`, a partir de `main` atualizada.

---

## Riscos

| Risco | Contenção |
|---|---|
| Números do `Resumo` divergirem dos do `Hoje` | Os dois saem dos mesmos serviços de hábito e tarefa, não de contas paralelas. |
| Fronteira da semana errada na virada de domingo | Teste com relógio fixo na segunda e no domingo. |
| Trocar o fuso quebrar o que já está gravado | Nada é convertido: as datas continuam as que foram gravadas. Só a leitura de "hoje" muda, e o teste do `/dia` cobre isso. |
| Aparelho com fuso errado reescrever o "hoje" em silêncio | O valor é validado (fuso desconhecido é recusado), e a Fatia 7 mostra o fuso em uso na tela de `Ajustes`, quando ela nascer para a permissão de notificação. |

---

## Próxima fatia

**A fatia de design** — ver `projeto-debito-de-design` na memória. Antes dela, vou te pedir
exemplos de apps de iPhone que você acha bonitos, para a direção sair do seu gosto e não de uma
abstração.
