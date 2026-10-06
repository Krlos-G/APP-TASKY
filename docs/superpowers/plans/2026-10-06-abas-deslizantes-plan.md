# Plano — Abas que deslizam como num app nativo

- **Data:** 2026-10-06
- **Projeto:** Tasky — https://github.com/Krlos-G/APP-TASKY
- **Depende de:** Fatia 8 (concluída). Entra antes da Fatia 9.
- **Status:** aprovado em 06/10, mantendo o "toque no relógio sobe ao topo"

---

## O problema

Depois de uns dias de uso no iPhone, o relato do Carlos: a navegação por gestos está **lenta, às
vezes bugando**, e o gesto de voltar ou avançar **às vezes quebra a passagem de uma tela para a
outra**.

## O que o código mostra

1. **Cada troca de aba destrói a tela e cria outra do zero.** A aba nova busca os dados no
   servidor (o Railway fica nos EUA, ~230 ms), mostra o esqueleto, depois o conteúdo, depois a
   animação de entrada das listas. Num app nativo as abas continuam vivas.
2. **Ao soltar o dedo, a tela atual volta ao centro na hora, e só depois a nova entra.** Se o
   código da aba ou os dados demoram, a tela velha parece desistir do gesto e a nova aparece um
   instante depois. É a explicação mais provável para a "quebra".
3. **Durante o arrasto só a tela atual se mexe**, e ao lado dela não há nada: a aba vizinha ainda
   não existe. No Instagram, a próxima tela vem junto com o dedo.
4. **Cada movimento do dedo faz o Angular conferir o app inteiro** (zone.js) e move a tela com
   `left`, o que obriga o navegador a refazer o layout e o blur dos cartões a cada quadro. No
   iPhone isso pesa: é a lentidão.
5. Um gesto feito durante uma troca ainda em andamento calcula a vizinha a partir da tela antiga.

---

## A proposta: as quatro abas vivas, lado a lado

- **Uma trilha** com Hoje, Resumo, Hábitos e Tarefas lado a lado, todas montadas. O dedo arrasta a
  trilha, e a vizinha aparece junto. Ao soltar, a trilha **termina o movimento** até a aba certa —
  sem voltar ao centro, sem tela em branco.
- **As abas continuam vivas:** trocar não busca do zero nem mostra esqueleto. Quando uma aba fica
  visível, ela atualiza os dados por baixo, mantendo o que já mostra até a resposta chegar —
  marcar um hábito no Hoje e ir para Hábitos mostra o hábito marcado.
- **Cada aba guarda a própria rolagem.**
- **O arrasto roda fora do Angular**, com `transform` e um quadro por vez (`requestAnimationFrame`):
  enquanto o dedo se move, o Angular nem fica sabendo.
- A pílula da barra acompanha o arrasto da tela; tocar numa aba da barra desliza a trilha até ela.
- **A URL continua a mesma** (`/hoje`, `/tarefas?filtro=…`): voltar da edição reabre a aba certa.

## Decisões

| Decisão | Escolha | Por quê |
|---|---|---|
| Rolagem | **A da página inteira, com a posição guardada por aba** | O Carlos quer manter o "toque no relógio sobe ao topo", que no iOS só funciona na rolagem da página. Em repouso, só a aba visível está na página. **Durante o movimento** (arrasto ou deslize), a aba atual e a vizinha viram "quadros" fixos na tela, cada um deslocado para cima pela própria rolagem; ao terminar, a aba de destino volta à página e a rolagem dela é restaurada. |
| Botão "+" | **Continua `position: fixed`, sem mudança** | O quadro é o elemento que se move com `transform`, e ele tem o tamanho da tela: o "+" passa a se posicionar por ele e anda junto no gesto, como num app nativo. O deslocamento vertical do quadro é feito com `top`, não com `transform`, para o "+" não se posicionar pela página inteira. Foi esse cuidado que faltou ao gesto atual, que por isso usava `left`. |
| Carregamento | **As quatro abas num pacote só** | Hoje cada uma é baixada na primeira visita. São poucos KB a mais na abertura, e o service worker já guarda tudo. |
| Desktop | **A mesma trilha, sem gesto** | Clicar na lateral desliza para a aba. |
| Telas fora das abas | **Como hoje** | Rotina, Ajustes e edição de tarefa não mudam. |
| O que sai | **A diretiva atual do deslizar e a animação de entrada entre abas** | A própria trilha passa a ser a animação. |



---

## Etapa 1 — A trilha, sem gesto

- Uma rota só para as quatro abas, que reaproveita o mesmo componente ao trocar de aba.
- O componente da trilha: as quatro telas montadas, só a aba da URL na página, troca pela barra
  deslizando entre os dois quadros, rolagem guardada e restaurada por aba.
- Atualizar ao ficar visível, sem esqueleto quando já há dados.
- Testes: trocar de aba não recria a tela; voltar a uma aba atualiza os dados; a URL acompanha a
  aba; o filtro de Tarefas continua vindo da URL.

**Resultado (06/10):** conferido no navegador. As quatro abas nascem montadas e carregam juntas.
Tocar na barra desliza os dois quadros: a aba que sai segue mostrando o ponto onde estava rolada, e
o "+" anda junto. No fim, a aba nova volta à página na rolagem dela — Hoje voltou nos 120 px em
que tinha ficado — e as voltas atualizam em silêncio, sem esqueleto. No desktop o palco cobre só a
coluna do conteúdo. Um achado no caminho: Tarefas, por só ouvir a URL dela, nascia vazia quando o
app abria em outra aba; agora carrega o filtro padrão ao nascer. Sem o gesto antigo, até a Etapa 2
trocar de aba é só pela barra.

## Etapa 2 — O gesto

- Arrasto fora do Angular, com `transform` e `requestAnimationFrame`. Reaproveita as contas de
  `gestos.ts` (decidir o eixo; soltar decide se troca) — elas já têm testes.
- Ao soltar, a trilha termina o movimento com a curva do iOS; um gesto durante a animação espera
  ela acabar.
- A pílula da barra acompanha o dedo; "reduzir movimento" troca sem animar.

**Resultado (06/10):** conferido no navegador com toques sintéticos. No meio de um arrasto de
100 px, as três abas andam juntas (a vizinha já aparece entrando) e a pílula vai a 1,27, a caminho
da aba seguinte. Ao soltar, a URL troca na hora e as abas terminam o movimento de onde o dedo
largou. Arrasto curto e lento desiste e devolve a rolagem; peteleco troca; nas pontas a tela anda
um quarto do dedo; arrasto vertical não monta quadro. A pílula recebe o arrasto direto no
elemento (`PilulaDaBarra`), sem passar pelo Angular. Um achado: `setPointerCapture` lança erro se
o navegador já assumiu o toque, e isso interrompia o movimento — agora o gesto segue sem a captura.

## Etapa 3 — No iPhone

Você testa no dia a dia; ajustamos sensibilidade e duração com base no que sentir.

**Primeira rodada (06/10): "falta fluidez e a sensibilidade requer esforço demais".** O que o
código explicava, e o que mudou:

- **O iPhone roubava o gesto.** Com `touch-action: pan-y` e eventos de ponteiro, um arrasto um
  pouco inclinado virava rolagem, o iPhone cancelava o gesto e a tela voltava — só funcionava um
  traço perfeitamente reto. Agora a trilha ouve **eventos de toque**, com o `touchmove` não passivo,
  decide o lado em 6 px (antes da folga do iPhone para começar a rolar) e, decidido, chama
  `preventDefault`: o gesto é do app.
- **Lado vale até 45° de inclinação** (antes o lado tinha de vencer o vertical por 20%).
- **O peteleco quase nunca contava:** a velocidade vinha só dos dois últimos movimentos, e o dedo
  desacelera ao levantar. Agora é medida nos últimos 100 ms. Limites afrouxados: um quinto da tela
  ou 0,3 px/ms com 20 px.
- **Fluidez:** enquanto as abas se movem, o blur dos cartões fica desligado (`--desfoque: none` no
  palco) — andando, cada cartão refaria o blur a cada quadro, nas três abas. Os quadros ganharam
  camada própria (`will-change`) e `contain: layout paint`.

Conferido no navegador com eventos de toque: deslize inclinado de ~31° troca; peteleco de 52 px
que freia no fim troca; arrasto curto e lento volta; o blur some no movimento e volta igual.

**Segunda observação (06/10): "em Tarefas › Hoje, da metade da tela para baixo o gesto não
funciona".** O gesto era ouvido só na trilha, que tem a altura do conteúdo: numa aba curta, o dedo
na parte vazia caía fora dela. Agora a trilha ouve a área inteira do conteúdo (o `main`), que
passou a ocupar a tela toda (`.app` em coluna flexível, `.conteudo` com `flex: 1`). Como escuta um
elemento que não é dela, a trilha se desliga ao sair da tela — um teste provou que, sem isso,
deslizar em Rotina navegava sozinho.

---

## Riscos

| Risco | Como trato |
|---|---|
| A fluidez só se julga no iPhone | Cada etapa vai ao ar sozinha; o veredito é seu. |
| O blur dos cartões custa GPU enquanto a trilha se move | Se engasgar, desligamos o blur só durante o arrasto (conferindo se pisca na volta). |
| Rolagem dentro da aba com o teclado do iOS (Hábitos tem formulário) | Conferir no aparelho na Etapa 3. |
| Mexe nas quatro telas principais | Os testes delas vão acusar o que mudar no carregamento. |
