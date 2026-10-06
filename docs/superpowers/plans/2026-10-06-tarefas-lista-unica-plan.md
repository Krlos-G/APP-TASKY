# Plano — Tarefas numa lista só, por quando

- **Data:** 2026-10-06
- **Projeto:** Tasky — https://github.com/Krlos-G/APP-TASKY
- **Depende de:** abas deslizantes (`2026-10-06-abas-deslizantes-plan.md`)
- **Status:** aprovado em 06/10

---

## O problema

Na tela de Tarefas, o filtro **Hoje** abre quase sempre vazio — o que é de hoje já aparece na tela
Hoje — e o resto está espalhado em cinco filtros (Hoje, Próximas, Atrasadas, Sem data, Concluídas).
O Carlos: "ter uma tela sem conteúdo na maioria das vezes não é bom".

## A proposta (aprovada como direção em 06/10)

**Uma lista só, organizada por quando**, sem o seletor de filtros:

```
ATRASADAS          ← dia planejado no passado, em vermelho
HOJE
AMANHÃ
SEXTA, 9 DE OUTUBRO   ← cada dia seguinte com o próprio cabeçalho, sem limite
SEM DATA
Concluídas (12) ▾  ← recolhidas no fim, abrem com um toque
```

- **Seção vazia não aparece.** A tela só fica vazia quando não há nenhuma tarefa por fazer — e aí
  vira um convite para criar a primeira.
- **Todas as tarefas com data**, por mais longe que estejam (decisão do Carlos).
- **Concluídas recolhidas no fim** (decisão do Carlos), com a contagem visível.

## Decisões

| Decisão | Escolha | Por quê |
|---|---|---|
| Buscar as pendentes | **Filtro novo `PENDENTES` na API**: todas as tarefas a fazer, por dia (sem data no fim) e, dentro do dia, na ordem de execução que já existe | Um pedido só, em vez de juntar quatro filtros. |
| Concluídas | **Buscadas junto**, para a contagem aparecer; a seção sempre abre recolhida | Recolher é estado de tela, não preferência a guardar. |
| Concluir uma tarefa | **Fica no lugar, marcada**, e vai para Concluídas na próxima atualização (ao voltar à aba) | É como a tela já funciona: nada some debaixo do dedo, e o engano se desfaz com outro toque. |
| Agrupar | **No front, numa função pura** com testes | É apresentação: "hoje", "amanhã" e o nome do dia dependem do dia do aparelho, que o front já sabe (`TimeProvider`). |
| Data em outro ano | **"Sexta, 9 de outubro de 2027"** | Sem o ano, uma tarefa do ano que vem pareceria desta semana. |
| Filtro na URL (`?filtro=`) | **Sai** | Sem filtros, a volta da edição só precisa reabrir a aba — e a trilha já guarda a rolagem dela. |
| Filtros antigos da API | **Ficam**, sem uso pelo front | Não custam nada e não vale mexer no que funciona; saem numa limpeza futura, se fizer sentido. |

---

## Etapa 1 — `PENDENTES` no backend

- `FiltroTarefa.PENDENTES`; consulta das tarefas a fazer do usuário; ordem por dia planejado (sem
  data no fim) e, dentro do dia, a `ORDEM_DE_EXECUCAO` de sempre.
- Testes: traz só as a fazer (nem feitas, nem canceladas); a ordem; isolamento entre usuários.

## Etapa 2 — A tela

- Função pura `agruparPorQuando(tarefas, hoje)` → seções com título; testes cobrindo atrasadas,
  hoje, amanhã, dia seguinte por extenso, outro ano, sem data e seção vazia que some.
- Tela de Tarefas refeita: as seções, a seção de concluídas recolhida no fim com a contagem, o vazio
  convidativo, concluir deixando no lugar, atualizar ao voltar à aba (em silêncio, como já faz).
- Sai o seletor de filtros e o filtro da URL; os links de edição passam a voltar para `/tarefas`.
- Testes da tela atualizados.

**Resultado (06/10):** conferido no navegador, claro e escuro, com tarefas de teste no banco
local: Atrasadas (com "de 25/09"), Hoje, Amanhã, "sexta, 9 de outubro", "terça, 5 de janeiro de
2027" e Sem data, e "Concluídas (9)" no fim, abrindo com a seta girando. Um achado: a linha de
atalho do Resumo (Rotina, Ajustes), de onde o estilo da linha de concluídas foi copiado, tinha o
ícone encostado na borda do cartão (0 px, contra 16 px das outras linhas) desde a fatia de design.
Corrigido nos dois.

**Pedido do Carlos antes do PR: animar o recolher das concluídas.** A lista fica numa gaveta que
anima a linha do grid de `0fr` a `1fr` (o jeito de animar até a altura do conteúdo que funciona no
Safari), com `@starting-style` para a primeira abertura também deslizar. A lista só é criada na
primeira abertura — as concluídas acumulam — e depois fica, `inert` quando fechada, para o fechar
também deslizar. Medido no navegador: abre 0 → 409 → 462 → 475 px; fecha 475 → 96 → 19 → 0.

## Etapa 3 — Conferência

No navegador (claro, escuro, celular e desktop) e depois no seu iPhone.

---

## Riscos

| Risco | Como trato |
|---|---|
| A lista de concluídas cresce para sempre | Hoje já é assim no filtro Concluídas. Se pesar, limitamos às mais recentes numa próxima vez. |
| Muitas tarefas futuras deixam a lista longa | É o pedido ("todas as tarefas"); as seções por dia ajudam a ler. Se incomodar, recolhemos os dias distantes. |
