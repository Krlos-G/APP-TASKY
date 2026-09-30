# Plano de Implementação — Fatia de Design

- **Data:** 2026-09-27
- **Projeto:** Tasky — https://github.com/Krlos-G/APP-TASKY
- **Spec de referência:** `docs/superpowers/specs/2026-08-30-app-rotina-pessoal-design.md`
- **Depende de:** Fatia 7 (concluída, PR mergeado)
- **Status:** aguardando aprovação

---

## Objetivo da fatia

**Nada de funcionalidade nova.** As mesmas 9 telas, os mesmos endpoints, o mesmo backend intocado —
vestidos. É a única fatia do projeto cujo objetivo é você *querer* abrir o app.

A régua não é "Human Interface Guidelines": são os dois apps que você apontou.

### O que cada referência entrega — e o que ela não entrega

| Referência | O que eu pego | O que eu **não** pego |
|---|---|---|
| **Things 3** | Respiro. Separação por espaço e cor, quase sem linha. Tipografia como hierarquia, não negrito espalhado. Resposta suave ao concluir. | O visual "caderno" e a barra lateral de projetos — o Tasky tem 4 abas fixas, não uma árvore. |
| **Atividade / Fitness** | A **barra de abas flutuante** (o print que você mandou) e a cor que significa — laranja = streak. | **O anel.** Você pediu outra coisa, e concordo: ele mostra uma fração e esconde o número. Também ficam de fora os gradientes vivos e o fundo preto sempre. |
| **Nativos da Apple** | Cartões agrupados sobre fundo cinza, a escala de tipografia do iOS, o azul do sistema, os raios. | Os separadores densos de lista — o Things ganha essa disputa: espaço no lugar de linha. |

Traduzindo o seu "sóbrio + nativo puro": **a gramática é da Apple, o espaçamento é do Things,
e a única cor quente do app é o streak.**

### Critério de pronto

1. Abro o Tasky no iPhone e ele não parece um site: tipografia, raios, espaçamento e cor são do
   vocabulário do iOS.
2. Marcar um hábito ou tarefa tem resposta visual imediata — hoje é "clicou, sumiu, voltou".
3. Nenhuma tela mostra `Carregando…` em texto puro.
4. Os 4 emoji da tab bar viraram ícones desenhados, e a barra virou a cápsula flutuante de vidro.
5. `prefers-color-scheme: dark` continua certo, token por token — o dark de hoje foi derivado do roxo.
6. Os 114 testes do front passam, build sem aviso, e **zero linha de backend alterada**.

**Fora do escopo** (pedido explícito seu): Rotina arrastável, ilustrações de estado vazio,
animação de transição entre rotas, webfont, biblioteca de componentes.

---

## Onde estamos hoje (levantei antes de escrever)

| Peça | Estado atual |
|---|---|
| Tokens | 7 cores, `--raio: 12px`. Nenhum token de espaçamento, tipografia, sombra ou tempo. |
| Cor primária | `#4f46e5` — um roxo de web app. |
| Tipografia | 8 tamanhos ad hoc entre `0.7rem` e `1.25rem`. **Nenhum deles é 17px**, o corpo do iOS. |
| Vermelho | `crimson` escrito à mão em 4 arquivos. |
| `.botao` | Existe **4 vezes** em SCSS diferentes — e já divergiu: fundo `--cor-fundo`, `--cor-superficie` e `transparent`. |
| Transições | **Zero.** Nenhum `transition`, `animation` ou `@keyframes` no projeto. |
| Ícones | 4 emoji (`📅 📊 🔥 ✓`) em `app.component.ts`. |
| Carregamento | `Carregando…` em texto, em 6 telas. |
| SCSS total | 1 769 linhas em 10 arquivos. |

---

## Decisões desta fatia

| Decisão | Escolha | Por quê |
|---|---|---|
| **Componentes globais antes de pintar** ⚠️ | Extrair `.botao`, `.cartao`, `.campo`, `.pilula`, `.vazio-cartao` para `styles/` | `.botao` existe 4 vezes e **já divergiu em 3**. Pintar as cópias é pintar 8 vezes e errar em algumas. Esta etapa não muda visual nenhum — ela é o que torna as outras baratas. |
| Fonte | **`system-ui`, sem webfont** | No iPhone `system-ui` **é** o SF Pro. Carregar um Inter por CDN deixaria *menos* nativo, não mais — e custaria request no carregamento. |
| Cor primária | Azul do sistema: `#007AFF` claro / `#0A84FF` escuro | "Nativo puro" pede o azul que o iOS já te ensinou a ler como "toque aqui". O roxo era escolha de web app. |
| Segunda cor | Laranja do sistema `#FF9500` / `#FF9F0A`, **só para streak** | O único empréstimo de cor da Atividade. Cor com significado, não decoração. |
| `crimson` | Vermelho do sistema `#FF3B30` / `#FF453A`, em token | `crimson` é um vermelho de HTML de 1996 e está hardcoded em 4 arquivos. |
| Tipografia | Tokens espelhando os *text styles* do iOS (corpo = 17px) | É o que faz o texto "sentar" certo num iPhone. E acaba com os 8 tamanhos improvisados. |
| Espaçamento | Escala de 4pt em tokens | O respiro do Things vem de espaçamento consistente, não de um `margin` a mais aqui e ali. |
| **Barra de abas flutuante** | Cápsula de vidro solta do rodapé, com pílula na aba ativa | O print que você mandou. É a peça mais visível do app e a que mais muda a sensação de "iOS novo". Fallback sólido onde `backdrop-filter` não existir. |
| Progresso do dia | **Segmentos**, um por item — sem anel | O anel esconde justamente o que interessa. Com segmentos você vê *quantas coisas tem hoje* e *quantas caíram* no mesmo relance. Acima de ~12 itens degrada para barra contínua. |
| Streak | **Trilha** — faixa de 14 dias com entalhes, a sequência acesa encostando em hoje ✅ | Escolhido por você em 27/09 entre três caminhos. Ele *mostra* o streak em vez de afirmar um número, e deixa o dia da quebra visível — que é metade do valor da coisa. Não inventa regra nenhuma: usa o que o backend já devolve. O histórico detalhado continua atrás do toque, como hoje. |
| Concluído | **Só escurecer, sem riscar** ✅ | Pedido seu em 27/09. O círculo verde já diz "feito"; o risco por cima é redundância sobre redundância. Vale para hábito e tarefa, nas duas telas. |
| Ícones | **SVG inline nosso**, num componente `<app-icone>` | SF Symbols não é licenciado para web. Uma biblioteca seria uma dependência para ~10 ícones. Desenhados na geometria do SF: 24px, traço 1.75, pontas redondas, `currentColor`. |
| Carregamento | **Esqueleto** no lugar de `Carregando…` | O esqueleto tem a forma do que vem, então a tela não salta quando os dados chegam. |
| Movimento | `220ms cubic-bezier(.32,.72,0,1)`, e **tudo desligado** em `prefers-reduced-motion` | Essa curva é a do iOS: sai rápido, chega devagar. E respeitar a preferência de acessibilidade é uma linha de CSS. |
| Dark mode | Revisado token a token, com os cinzas da Apple (`#000` de fundo, `#1C1C1E` de superfície) | Os valores de hoje foram derivados do roxo. Trocando a primária, eles ficam órfãos. |

A marcada com ⚠️ é a única decisão de ordem que muda o plano: **a Etapa 1 não produz mudança
visual nenhuma** e é a mais importante da fatia. Se preferir ver cor logo, eu inverto — mas aí
repintamos as mesmas coisas várias vezes.

---

## Etapa 1 — Fundação: tokens e componentes globais

Corrigindo o que eu tinha escrito antes: **as telas mudam de aparência aqui, sim** — trocar a
paleta e a escala de tipografia nos tokens repinta o app inteiro de uma vez. O que *não* muda é o
arranjo: nenhuma tela é reorganizada nesta etapa. Pintar por cima dos tokens velhos e trocá-los
depois seria fazer o trabalho duas vezes.

- Quebra `styles.scss` (54 linhas, virou pequeno demais para o que vem) em `src/styles/`:
  `_tokens.scss`, `_base.scss`, `_componentes.scss` — importados por `styles.scss`.
- Tokens novos: escala de tipografia, escala de espaçamento 4pt, raios (`cartao`/`botao`/`pilula`),
  sombra sutil, duração e curva de transição, e a paleta redesenhada nos dois modos.
- Promove para global: `.botao` (+ `--primario`, `--perigo`, `--curto`, `--texto`), `.cartao`,
  `.campo`, `.pilula`, `.vazio-cartao`. `.alerta` e `.aviso` já são globais e entram na revisão.
- **Apaga as cópias locais** nos 8 SCSS de página.

**Verificação:** `npm test` (114 devem continuar verdes — nenhum asserta em estilo), `npm run build`
sem aviso, e uma passada nas 9 telas no navegador conferindo que nada desmontou.

---

## Etapa 2 — Ícones e a barra de abas

### Ícones

- `core/ui/icone.component.ts`: `<app-icone nome="hoje" />`, `viewBox="0 0 24 24"`, traço 1.75,
  `currentColor`, `stroke-linecap: round`. Standalone, `OnPush`, sem dependência.
- Conjunto inicial: `hoje`, `resumo`, `habitos`, `tarefas`, `check`, `mais`, `lapis`, `lixeira`,
  `sino`, `engrenagem`, `chevron`.
- Tab bar passa a usar o componente; `icone: string` sai de `app.component.ts`.
- Botões de ação curtos (`Editar`, `Remover`) ganham ícone ao lado do texto — não em lugar dele:
  ícone sozinho vira adivinhação.

### A barra flutuante

No celular, a `.nav` sai do rodapé e vira cápsula solta:

- `position: fixed` com margem lateral e `bottom: calc(env(safe-area-inset-bottom) + 8px)` —
  flutuando sobre o conteúdo, não grudada na borda.
- Material de vidro: `backdrop-filter: blur(24px) saturate(180%)` (com prefixo `-webkit-`, que é
  o que o Safari do iOS usa) sobre um fundo translúcido.
- **Aba ativa numa pílula preenchida**, que desliza de uma aba para outra na curva do iOS.
- `@supports not (backdrop-filter: blur(1px))`: cai para superfície sólida. O vidro é enfeite; a
  navegação não pode depender dele.
- O `padding-bottom` do `.conteudo` cresce para a barra não cobrir o fim da lista.
- **Desktop (≥768px) não muda de estrutura:** continua a coluna lateral, só repintada. Cápsula
  flutuante é gramática de celular.

**Verificação:** o `aria-hidden` do `<span>` atual migra para o `<svg>`, e o `aria-label` do link
continua carregando o nome da aba — teste de acessibilidade da nav segue passando. Conferir a
barra sobre conteúdo rolando (é aí que o vidro aparece) e com a lista no fim.

---

## Etapa 3 — `Hoje`

A tela que você abre todo dia, e a maior (362 linhas de SCSS). Ganha o tratamento completo:

- Cabeçalho em *large title* (34px) com a data por extenso em apoio, como nos apps da Apple.
- **Progresso em segmentos** no lugar da barra: um segmento por item devido hoje, cantos
  arredondados, preenchidos na primária. Número grande ao lado, não dentro.
- Blocos, hábitos e tarefas em cartões agrupados sobre o fundo cinza.
- Cartão do bloco "agora" destacado por cor de fundo, não por borda.
- Linha de hábito: alvo de toque mínimo de 44px (o mínimo da Apple) e o círculo de marcação à
  esquerda, como no Things.

---

## Etapa 4 — `Resumo` e `Hábitos`

- **Resumo:** números grandes em cartão, o tally da semana com hierarquia clara. É a tela mais
  "dashboard" do app e a que ganha mais com tipografia.
- **Hábitos:** o streak vira a **trilha** — uma faixa de 14 dias com entalhes, sempre visível na
  linha do hábito, com a sequência acesa em laranja encostando no dia de hoje. O número grande
  com a chama fica ao lado, como legenda do que você já está vendo. O histórico detalhado em
  pílulas continua recolhido atrás do toque, como na Fatia 4 — a trilha é o resumo, ele é o
  detalhe.

---

## Etapa 5 — `Tarefas`, `Tarefa-edição` e `Rotina`

- **Tarefas:** as abas de filtro viram *segmented control* — o controle que o iOS usa para
  exatamente isso. Resolve de passagem o débito do `[ + ]` flutuante sobre o contador.
- **Tarefa-edição:** formulário em cartões agrupados, campos com a altura e o raio nativos.
- **Rotina:** **sem reconstruir** (o arrastável ficou fora). Só respiro, cartões e hierarquia,
  para a pilha de formulários parar de parecer uma pilha de formulários.

---

## Etapa 6 — `Ajustes` e `Login`

- **Ajustes:** a tela que mais se aproxima de um app da Apple por natureza — cartões agrupados
  com rótulo de seção acima, em caixa alta pequena.
- **Login:** a primeira coisa que o app mostra, e hoje a mais crua (94 linhas). Centrada, marca
  em cima, respiro.

---

## Etapa 7 — Movimento

O que mais separa "app" de "site", e a etapa que você pediu explicitamente.

- **Toque:** `:active { transform: scale(.97) }` em botões e linhas clicáveis.
- **Marcação:** o círculo preenche com um leve *overshoot* e o texto escurece na transição, sem
  risco. Vale para hábito e tarefa — os dois já são otimistas no clique.
- **Esqueletos:** classe global `.esqueleto` com brilho passando. Cada uma das 6 telas mostra o
  esqueleto **da forma que ela vai exibir**, para os dados chegarem sem salto.
- **Entrada de lista:** *fade* curto com deslocamento de poucos pixels, escalonado — o que dá a
  sensação de suavidade do Things.
- **`prefers-reduced-motion: reduce`:** desliga transição, animação e escala. Uma regra, no fim
  de `_base.scss`.

### A barra deslizável (pedido do Carlos em 28/09)

Na barra do iOS 26 você não só toca numa aba: **encosta e arrasta o dedo pela barra**, a pílula
acompanha o dedo e a aba troca conforme você passa por cima de cada ícone. Hoje a nossa só aceita
toque solto.

Como fazer:

- A pílula sai do `background` do item ativo e vira **um elemento só**, posicionado por
  `transform: translateX()`. É isso que permite ela deslizar em vez de piscar de um item para o
  outro — e já é o que a Etapa 7 faria de qualquer jeito para animar a troca por toque.
- O arrasto é `pointerdown` na barra + `pointermove` com `setPointerCapture`, descobrindo qual
  aba está sob o dedo por `elementFromPoint`.

**A decisão que precisa da sua opinião quando chegarmos lá:** trocar de rota *durante* o arrasto
ou *ao soltar*? No iPhone a troca é ao vivo, mas lá as abas já estão todas carregadas. Aqui cada
aba é uma rota `lazy` que busca dados — arrastar da Hoje até Tarefas dispararia três telas e três
rodadas de requisição no caminho. **Minha sugestão:** a pílula segue o dedo ao vivo (que é o que
se *sente*), e a rota troca ao soltar. Custa nada e não vira tempestade de requisição.

Cuidados: `touch-action: none` só na barra, para o arrasto não rolar a página junto; toque simples
e teclado continuam funcionando como hoje; e nada disso vale com `prefers-reduced-motion`.

### Decidido na implementação (30/09)

- **Ao soltar.** A pílula segue o dedo ao vivo; a rota troca quando o dedo sai da barra. A aba sob
  o dedo sai de conta (posição ÷ largura), não de `elementFromPoint`.
- **Deslizar a tela troca de aba** (pedido do Carlos, "como no Instagram"): só entre as 4 abas, sem
  dar a volta nas pontas — lá a tela resiste. Também ao soltar: passou de um quarto da tela ou foi
  um peteleco. Não vale começando dentro de formulário, nem com mouse.
- **A tela nova entra pelo lado da aba dela**, venha a troca de toque, arrasto ou deslize.
- **Sem View Transitions:** o instantâneo da tela achataria o vidro fosco. A entrada é uma
  animação CSS, e a direção vem de uma variável na raiz.
- A tela acompanha o dedo com `left`, não `transform`: o botão flutuante é `position: fixed`
  dentro da página e passaria a se posicionar a partir dela.

---

## Etapa 8 — Fechamento

- Revisão das 9 telas nos dois modos (claro/escuro) e nas duas larguras (iPhone e desktop).
- Conferir que a tab bar respeita `env(safe-area-inset-bottom)` com o visual novo.
- `npm test`, `npm run build`, e `npm run servir:build` para você ver no build de produção.
- Atualizar `projeto-debito-de-design.md`: o que saiu da lista e o que sobrou (Rotina arrastável,
  estados vazios ilustrados).
- Mensagem de commit por etapa, como sempre.

### Resultado (30/09)

Revisadas 8 das 9 telas nas duas larguras, com varredura automática (estouro lateral, texto
cortado, controle escondido atrás da barra ou do "+") e olho nos dois temas. O Login ficou de fora:
com sessão aberta o guard redireciona, e ele já tinha sido conferido na Etapa 6. Três ajustes
saíram daqui:

- **Pílula fora das abas:** em Rotina e Ajustes ela ia para a posição −1 e escorregava para fora
  da barra ao sumir. Agora some onde estava.
- **Pílula dentro de uma tarefa:** a edição continua na seção Tarefas, como um detalhe aberto numa
  aba do iOS. O deslizar entre abas segue valendo só nas quatro telas principais.
- **"0/0 tarefas" no Resumo** (pendência de gosto da Fatia 6): o número do dia só aparece quando
  existe, como as atrasadas já faziam. A outra pendência, "100% com 1 atrasada", sumiu sozinha
  quando o cartão do dia trocou a porcentagem por segmentos.

A área segura de baixo está coberta (barra, "+" e respiro do conteúdo). **Passam para a Fatia 8,
porque só se conferem no iPhone instalado:** a área segura do topo (depende do estilo da barra de
status), o custo do blur na rolagem e a sensação dos gestos.

---

## Ordem e pontos de parada

```
1. Fundação (tokens + globais)   ← nenhuma mudança visual; a etapa que paga as outras
2. Ícones
3. Hoje                          ← aqui você já vê o app novo e pode mandar corrigir o rumo
4. Resumo + Hábitos
5. Tarefas + Edição + Rotina
6. Ajustes + Login
7. Movimento                     ← precisa de todas as telas prontas para não reajustar
8. Fechamento
```

**Ponto de parada natural: depois da Etapa 3.** A `Hoje` sozinha já mostra a direção inteira. Se o
resultado não te agradar, corrigir ali custa uma tela — depois da Etapa 6, custa seis.

---

## Riscos

| Risco | Tamanho | Como trato |
|---|---|---|
| **O gosto.** Eu descrevo "sóbrio + nativo" em palavras e entrego outra coisa. | **Alto** — é o risco real da fatia | Parada obrigatória depois da Etapa 3, com a tela no seu navegador. E se tiver print dos apps que você citou, me manda antes da Etapa 1. |
| Testes que assertam texto que eu mexi | Baixo | Nenhum spec asserta em `Carregando…`, e os textos de estado vazio ficam. Rodo a suíte a cada etapa. |
| Apagar `.botao` local e quebrar uma tela | Médio | É exatamente por isso que a extração é uma etapa só, com as 9 telas conferidas antes de pintar qualquer coisa. |
| Dark mode ficar pior que o de hoje | Médio | Os cinzas vêm dos valores reais da Apple, não de um `darken()`. E a Etapa 8 revisa tela por tela nos dois modos. |
| A escala de 17px estourar layout no desktop | Baixo | 17px é *maior* que os 0.9rem de hoje em vários lugares. Confiro nas duas larguras na Etapa 8. |

---

## Próxima fatia

**Fatia 8 — PWA + deploy.** É lá que *"recebo lembrete no iPhone no horário"* finalmente se prova,
e ela fica melhor depois desta: o polimento de PWA (ícone, splash, `theme-color`, tela de início)
sai já com a paleta e os ícones definitivos, em vez de ser refeito depois.

---

## Mudança de direção: vidro e gradiente (29/09)

Depois das Etapas 1–5, o Carlos trouxe referências de Figma e a direção evoluiu do "sóbrio +
nativo" para **vidro sobre gradiente**, coerente com a barra de abas e o botão flutuante, que já
eram de vidro:

- **Fundo:** gradiente suave na família do azul do sistema (azul no canto de cima, lavanda no de
  baixo; azul-marinho e índigo no escuro), numa camada fixa atrás de tudo — no iPhone o
  `background-attachment: fixed` é ignorado.
- **Vidro fosco de verdade nas superfícies** (`backdrop-filter`), a pedido do Carlos. Para o blur
  ter o que desfocar, o fundo ganhou **esferas de cor com contorno**: sobre um gradiente liso o
  blur fica invisível. Só a superfície de fora desfoca — campo dentro de cartão não desfoca de
  novo, porque blur dentro de blur custa em dobro e embaça o texto.
- **Contraste medido no pior caso** (texto cinza sobre o miolo de uma esfera): os primeiros valores
  reprovavam (4,2:1 no claro, 2,5:1 no escuro). O escuro virou **vidro fumê**, que escurece a esfera
  em vez de clarear, e o cinza secundário foi ajustado nos dois temas — agora 5,5:1 ou mais.
- **Custo não medido de verdade:** no ambiente de desenvolvimento a rolagem dá 30 quadros com e sem
  blur (é o teto do ambiente), então o veredito é do Safari no iPhone. Se engasgar, `--desfoque`
  é um token só: `none` desliga o blur dos cartões de uma vez, sem mexer em mais nada.
- **Borda branca interna** (`inset`) nas superfícies — é o que as faz ler como vidro.
- **Botão primário em gradiente** com brilho azul embaixo.
- **Campo-cartão** (rótulo dentro, valor grande, ícone à esquerda nos seletores), também tirado de
  uma referência dele.

Tudo passa por token: a mudança de visual foi quase inteira nos valores de `_tokens.scss`.
