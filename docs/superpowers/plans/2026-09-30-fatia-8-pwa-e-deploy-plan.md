# Plano de Implementação — Fatia 8: PWA + deploy

- **Data:** 2026-09-30
- **Projeto:** Tasky — https://github.com/Krlos-G/APP-TASKY
- **Spec de referência:** `docs/superpowers/specs/2026-08-30-app-rotina-pessoal-design.md` (seções 11 e 12)
- **Depende de:** Fatia 7 (mergeada) e fatia de design (branch `feature/design-app`, falta o PR)
- **Status:** aguardando aprovação

---

## Objetivo da fatia

Tirar o Tasky do `localhost`: no ar com HTTPS, **instalado na tela de início do iPhone** e
sincronizando com o PC. É aqui que se prova o critério que a Fatia 7 empurrou para cá — *"recebo
lembrete no iPhone no horário"* —, e com ele o risco guardado desde o início do projeto: a
pontualidade do Web Push no iOS.

**Critério de pronto:**

1. O Tasky responde em HTTPS no Railway, e um push na `main` só vira deploy com o CI verde.
2. Instalado pelo Safari, abre em tela cheia, com o ícone do Tasky e o gradiente passando por baixo
   do relógio.
3. O "enviar teste" dos Ajustes chega no iPhone, e um lembrete de hábito chega no horário com o app
   fechado.
4. Marco um item no PC e ele aparece marcado no iPhone, e o contrário.
5. Depois de um deploy novo, o app instalado avisa que há versão nova.
6. `mvn verify` e os testes do front passam; CI verde.

**Fora do escopo:** domínio próprio, backup automático, monitoramento de uptime, splash screen,
conta de desenvolvedor Apple.

---

## Decisões desta fatia

| Decisão | Escolha | Por quê |
|---|---|---|
| Hospedagem | **Railway, plano Hobby** | Decidido por você em 30/09. US$ 5/mês com US$ 5 de uso incluídos; o que passar disso é cobrado. |
| Front e API | **Mesmo endereço: o Spring serve o build do Angular** | O Safari bloqueia cookie de terceiro. Com domínios separados, o cookie de renovação da sessão vira "de terceiro" e o iPhone derrubaria o login. O front já chama `/api/v1` sem domínio, e o backend já tem `SameSite=Lax` e CORS vazio para esse caso. |
| Domínio | **Subdomínio grátis do Railway** (`*.up.railway.app`) | Trocar depois custa reinstalar o app, logar de novo e reativar as notificações — tudo fica preso ao endereço. Os dados ficam no servidor. Para uso pessoal é um custo pequeno. |
| Banco | **PostgreSQL 18** (imagem `postgres-ssl:18` do Railway) | A mesma versão do compose e do Testcontainers. |
| Memória da JVM | **Heap limitado (~256 MB) e SerialGC** | Sem limite, a JVM se dimensiona pela máquina do Railway, e ele cobra por GB de RAM. Medimos no ensaio local antes de subir. |
| `servir-build.mjs` | **Removido** | Existia para ensaiar o build de produção. A imagem Docker desta fatia faz o mesmo ensaio, e é ela que vai para produção. |
| Assunto do VAPID | **O endereço do app** (`https://…up.railway.app`) | O protocolo aceita `https:`; assim nenhum e-mail seu vai nos cabeçalhos para a Apple e o Google. |
| Swagger em produção | **Desligado por variável de ambiente** | Não precisa de código. |
| Backup ⚠️ | **Manual nesta fatia (`pg_dump`), automático na 9** | O Hobby não tem backup nativo do volume. Deixo o comando documentado no README. |
| Cache offline do dia ⚠️ | **Vai para a Fatia 9** | O spec lista na 8. Mas não é só configurar o service worker: sem rede, a renovação da sessão falha e o app manda para o login antes de chegar no dia. Mexer no fluxo de autenticação aqui aumenta o risco da fatia que já tem mais incógnitas. |
| Exportar | **Fatia 9** | O spec se contradiz: o texto lista na 8, a tabela de fatias na 9. Fico com a tabela. |

As marcadas com ⚠️ mudam o que o spec pedia e precisam do seu ok.

**Custo estimado:** app (~300 MB) + banco (~100 MB) ≈ 0,4 GB × US$ 10 ≈ US$ 4, mais CPU quase
ociosa e alguns centavos de volume. Deve ficar em torno dos US$ 5 inclusos: **US$ 5–7/mês** no
total. Confiro nas métricas do Railway depois de uma semana.

---

## Etapa 1 — Um endereço só: o Spring serve o front

Tudo no backend, testável sem Railway.

- `server.port: ${PORT:8080}` — o Railway injeta a porta.
- Arquivos estáticos em `classpath:/static/`, com **fallback para `index.html`** nas rotas do
  Angular (`/hoje`, `/tarefas/15`…). **Nunca em `/api`:** rota de API inexistente continua
  respondendo erro em JSON, não uma página.
- Segurança: `/api/**` segue exigindo token (com as exceções de hoje); o resto é público, porque é
  só o app.
- Cache: `index.html`, `ngsw.json` e `ngsw-worker.js` com `no-cache`, para um deploy novo ser
  percebido.
- Testes (MockMvc, com um `index.html` de mentira nos recursos de teste): `/hoje` sem token devolve
  o app; `/api/v1/dia` sem token continua 401; `/api/v1/nao-existe` não devolve `index.html`;
  `/actuator/health` responde.

## Etapa 2 — A imagem de produção e o ensaio local

- **`Dockerfile` na raiz, em três estágios:** Node 24 compila o front → Maven empacota o jar com o
  front dentro de `static/` → JRE 21 roda o jar como usuário sem privilégio. Os testes não rodam
  aqui; quem roda é o CI.
- **`.dockerignore`**, com `.env` na lista. Sem ele, o segredo local iria parar dentro da imagem.
- **Ensaio:** `docker build` e `docker run` apontando para o banco do compose. O app inteiro numa
  porta só, igual vai rodar no Railway. É aqui que confirmo que o service worker registra, que as
  rotas do Angular abrem direto e que o login funciona com cookie `Secure`.
- `docker stats` em repouso, para calibrar o limite de memória.
- Remove `frontend/scripts/servir-build.mjs` e o script do `package.json`.

**Ponto de parada:** se a imagem roda local, o deploy vira configuração.

## Etapa 3 — O PWA de verdade

- **Ícone do Tasky.** Hoje é o logo padrão do Angular, com fundo transparente, que o iOS pinta de
  preto. Te mostro 2 ou 3 desenhos antes; você escolhe. Saem: 180×180 opaco para o iOS, 192 e 512
  para o manifesto, e um 512 "maskable" com margem de segurança. O ImageMagick desta máquina gera
  os PNGs a partir do SVG.
- **Manifesto:** `theme_color` e `background_color` do gradiente (hoje é o roxo antigo `#4f46e5`),
  e ícones com `purpose` separado — `"maskable any"` no mesmo arquivo corta a arte em um dos dois
  usos.
- **Barra de status:** `black-translucent`, para o gradiente passar por baixo do relógio como num
  app nativo. O conteúdo ganha `env(safe-area-inset-top)` no topo. **Risco a conferir no aparelho:**
  nesse modo o relógio é sempre branco, e no tema claro o topo do gradiente é azul-claro. Se ficar
  ilegível, voltamos para `default`.
- `theme-color` separado para claro e escuro no `index.html`.
- **Aviso de nova versão (`SwUpdate`):** quando o service worker baixa uma versão nova, aparece um
  aviso "Nova versão disponível · Atualizar". E o app **procura versão nova sempre que volta para a
  frente** — no iPhone ele fica suspenso por dias sem recarregar, e só a checagem na abertura não
  bastaria. Teste unitário com um `SwUpdate` falso.

## Etapa 4 — Railway (a parte feita com você no painel)

Criar a conta, colocar o cartão e colar os segredos são ações suas; eu preparo tudo e te guio passo
a passo.

- **`railway.json` no repositório:** build pelo `Dockerfile`, healthcheck em `/actuator/health`,
  reinício em falha. A configuração fica versionada, não só no painel.
- **Projeto no Railway:** serviço Postgres 18 + serviço do app ligado ao GitHub, conversando pela
  rede privada. **App sempre ligado** (o modo que adormece mataria o agendador de lembretes).
  "Wait for CI" ligado: o deploy espera o CI da `main` passar.
- **Variáveis:** URL do banco montada com as referências do Railway
  (`jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`),
  **segredo JWT novo**, **par VAPID novo** (as chaves de desenvolvimento não vão para produção),
  cookie `Secure`, código de convite temporário, Swagger desligado, opções da JVM.
- Gerar o domínio `*.up.railway.app`.
- **Antes do primeiro deploy:** o PR da fatia de design precisa estar na `main`, e esta fatia
  também — o Railway publica o que está lá.

## Etapa 5 — No iPhone: instalar e provar

A etapa que responde se o projeto funciona. O que é seu e o que é meu:

- **Você:** cria sua conta com o código de convite. Depois **eu tiro o código das variáveis** e o
  registro fecha.
- **Você:** Safari → Compartilhar → "Adicionar à Tela de Início". O app instalado tem cookies
  separados dos do Safari, então o login é feito de novo dentro dele.
- Ajustes → ativar notificações → "enviar teste".
- Lembrete de hábito para daqui a alguns minutos, app fechado → chega no horário? Concluir antes →
  não chega?
- PC: mesmo endereço no navegador, marca algo, confere no iPhone.
- **A lista que a fatia de design deixou para o aparelho:** blur engasgando na rolagem
  (`--desfoque: none` desliga de uma vez), área segura em cima e embaixo, relógio legível, sensação
  dos gestos.
- **Pontualidade:** nos dias seguintes, você anota quando os lembretes chegam; eu comparo com a
  hora de envio nos logs do Railway. É o dado que decide se o plano B (Capacitor + conta Apple)
  entra em cena.

Sem Mac não há inspetor do Safari para o iPhone. Quando algo der errado só no aparelho, a
investigação é pelos logs do Railway e pelos seus prints.

## Etapa 6 — Fechamento

- README: como fazer deploy, as variáveis de produção e o comando de backup manual.
- Memória do projeto atualizada; mensagens de commit por etapa, como sempre.

---

## Ordem e pontos de parada

```
1. Spring serve o front          ← tudo testável localmente
2. Imagem + ensaio local         ← parada: se roda aqui, o resto é configuração
3. PWA (ícone, barra, SwUpdate)  ← parada curta para você escolher o ícone
4. Railway                       ← com você no painel; exige os PRs na main
5. iPhone                        ← o critério de pronto de verdade
6. Fechamento
```

As etapas 1–3 não dependem do Railway. Dá para seguir nelas enquanto você cria a conta.

---

## Riscos

| Risco | Tamanho | Como trato |
|---|---|---|
| **Web Push atrasa no iOS** | **Alto** — é o risco do projeto | A Etapa 5 mede. Se não servir: Capacitor + conta Apple, com notificação agendada no próprio aparelho. O backend já isola o envio atrás de `CanalNotificacao`. |
| Primeiro deploy não sobe (URL do banco, rede privada, variável faltando) | Médio | O ensaio local da Etapa 2 elimina a maior parte; o healthcheck e os logs mostram o resto. |
| Blur pesado demais no iPhone | Médio | `--desfoque: none` — um token só. |
| Relógio branco ilegível no tema claro | Baixo | Voltar para `default` é uma linha. |
| Perder dados sem backup nativo | Baixo agora, cresce com o uso | `pg_dump` manual documentado já nesta fatia; automático na 9. |
| Conta passar dos US$ 5 inclusos | Baixo | Heap limitado; conferir as métricas depois de uma semana. |

---

## Próxima fatia

**Fatia 9 — Exportar + endurecimento.** Com o que esta fatia empurrou para lá: cache offline do
dia, backup automático do banco e monitoramento de uptime.
