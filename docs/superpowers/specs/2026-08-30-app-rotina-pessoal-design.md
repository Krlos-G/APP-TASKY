# App de Rotina Pessoal — Documento de Design

- **Data:** 2026-08-30 (revisado em 2026-08-31 — versões de stack atualizadas, ver seção 15)
- **Autor:** Carlos (carlosgabrielcaetanobarbosa@gmail.com), em conjunto com Claude
- **Repositório:** https://github.com/Krlos-G/APP-TASKY — projeto **Tasky**
- **Status:** aprovado; plano da Fatia 1 escrito em
  `docs/superpowers/plans/2026-08-31-fatia-1-fundacao-plan.md`

---

## 1. Contexto e objetivo

Aplicação pessoal para organizar a rotina diária e reduzir procrastinação. O autor trabalha
100% home office e fica ocioso fora do horário de trabalho; a ideia é ter uma ferramenta que
ajude a **montar** e principalmente **seguir** uma rotina.

O coração do app é a combinação de três coisas:

1. **Agenda-modelo do dia em blocos de horário** (ex.: 07:00 treino, 09:00–12:00 foco no trabalho).
2. **Hábitos recorrentes com _streak_** (marcação feito/pulado e contagem de dias seguidos).
3. **Tarefas avulsas** que se encaixam nos espaços livres do dia.

Camada de engajamento priorizada pelo autor: **lembretes no horário** + **streak e progresso visual**.

### Não-objetivos (por enquanto)

- Não é um produto para escalar. É de uso pessoal, **um único usuário**.
- Sem app na App Store / TestFlight.
- Sem integrações profundas com iOS (widgets, Siri, Focus, Live Activities).
- Sem ritual de revisão noturna, sem hábito com meta numérica, sem "esqueci a senha" no MVP.

---

## 2. Usuário e restrições

- **Perfil técnico:** desenvolvedor fullstack júnior. Experiência com **Java (Spring Boot, Hibernate)**
  e **TypeScript (Angular)**. Confortável com front-end web e back-end/API. Aberto a aprender.
- **Máquina de desenvolvimento:** Windows 11. **Sem acesso a Mac.**
- **Uso do app:** **iPhone + PC, sincronizados.** Usa no navegador do PC durante o expediente e no
  iPhone quando sai da mesa.
- **Disponibilidade exigida:** o app precisa funcionar **o dia todo, todos os dias** (os lembretes
  dependem disso).
- **Orçamento:** preferência por custo baixo. ~US$ 5/mês é aceitável; US$ 0 é melhor ainda.
- **Versionamento:** repositório Git publicado no GitHub (privado).

---

## 3. Decisão de stack

### Caminho escolhido — PWA + backend Spring Boot

- **Front:** Angular **22** como **PWA** (Progressive Web App). No iPhone o usuário faz "Adicionar
  à Tela de Início" e ganha um ícone/app; no PC é só abrir no navegador. Layout responsivo serve os
  dois. Testes com **Vitest**.
- **Back:** Spring Boot **4.1.1** (Java 21, **Maven**) + **PostgreSQL** + Hibernate/Spring Data JPA.
  API REST. **Flyway** para versionar o schema.
- **Sincronização:** trivial — é cliente/servidor; o Postgres é a fonte única da verdade.
- **Notificações:** Web Push (padrão VAPID), com o backend agendando e disparando.

### Por que esse caminho

O requisito "iPhone + PC sincronizados" já empurra para uma aplicação web com backend + banco, e
isso é **exatamente a stack que o autor já domina** (Angular + Spring Boot + Postgres).
É o caminho que:

- roda 100% a partir do Windows;
- não exige Mac, nem conta de desenvolvedor Apple (US$ 99/ano), nem revisão de App Store;
- entrega algo instalável no iPhone mais rápido;
- tem atualização instantânea (deploy do front, sem revisão).

### Alternativas consideradas e descartadas

- **Caminho B — React Native (Expo) + mesmo backend.** Ganho real: `expo-notifications` com
  **agendamento local nativo** (dispara na hora certa, offline, sem servidor). Contras: framework
  novo; como o autor também quer usar no PC, precisaria manter **duas interfaces** (ou aceitar
  `react-native-web`); build na nuvem (EAS) + conta Apple US$ 99/ano para um app "de verdade"
  (com Apple ID grátis o app expira a cada 7 dias). Mais peças móveis, entrega mais lenta.
  **Fica como plano de contingência:** se a pontualidade do Web Push no iOS não bastar, todo o
  backend/API/modelo de dados é reaproveitado e pluga-se um shell Expo por cima.
- **Caminho C — Nativo SwiftUI.** Melhor integração com iOS, mas exige **Mac + Xcode**, Swift é
  100% novo, e ainda precisaria de um front web à parte. Inviável no cenário atual.

---

## 4. Arquitetura geral

```
┌─────────────────────────────┐         HTTPS/JSON        ┌──────────────────────────────┐
│  FRONT — Angular PWA        │  ───────────────────────► │  BACK — Spring Boot (Java 21) │
│                            │  ◄───────────────────────  │                              │
│  • UI responsiva (cel+PC)  │                           │  • API REST (/api/v1)        │
│  • Service Worker (ngsw)   │      Web Push (VAPID)      │  • Spring Data JPA/Hibernate  │
│  • SwPush (assinatura)     │  ◄─────────────────────────│  • Spring Security + JWT      │
│  • IndexedDB (cache dia)   │                           │  • Flyway (migrations)        │
└─────────────────────────────┘                           │  • Scheduler @Scheduled       │
                                                         │    (varre lembretes 1x/min)  │
                                                         └───────────────┬──────────────┘
                                                                         │ JDBC
                                                                 ┌───────▼────────┐
                                                                 │  PostgreSQL    │
                                                                 └────────────────┘
```

### Decisões estruturais

- **1 repositório Git**, com `/frontend` (Angular) e `/backend` (Spring Boot).
- **Front:** Angular 22 + `@angular/pwa` (service worker `ngsw` + manifest). O serviço `SwPush` do
  Angular cuida da assinatura de push e de receber as mensagens. Estado com `signals` + services;
  sem biblioteca de estado pesada no início.
- **Back:** Spring Boot 4.1.1 (Web, Data JPA, Security, Validation), Flyway, PostgreSQL,
  springdoc-openapi 3.1.x. Push com a lib `nl.martijndwars:web-push` (**ver risco na seção 15**).
  Agendador = `@Scheduled(fixedDelay = 60s)` (instância única, sem lock distribuído).
- **API versionada** sob `/api/v1` desde o início.
- **Dev local:** `docker-compose` apenas com o Postgres; front e back rodam na máquina.
- **`Clock` injetável no Spring** e `TimeProvider` no Angular — nunca `Instant.now()` / `new Date()`
  soltos (facilita testar meia-noite, atraso de lembrete, fuso).

---

## 5. Modelo de dados

Princípio central: **o template nunca é mutado pelo uso diário.** O "dia de hoje" é _derivado_ do
template + hábitos + tarefas; o que persiste é o log de conclusão.

### Convenção de nomes

- **Tabelas e colunas:** `snake_case` em português (`bloco_modelo`, `hora_inicio`).
- **Entidades/classes Java:** `PascalCase` em português, com `@Table` / `@Column` mapeando para o
  `snake_case`.
- **Enums:** valores em português maiúsculo (`FEITO`, `A_FAZER`).
- **JSON da API:** `camelCase` português (`horaInicio`, `dataPlanejada`).
- **`streak`** mantém o nome em inglês, no código e na UI.

### Entidades

| Entidade (Java) | Tabela | Campos principais |
|---|---|---|
| **Usuario** | `usuario` | `email`, `senha_hash`, `nome_exibicao`, `fuso_horario` (IANA, ex. `America/Sao_Paulo`), `criado_em` |
| **ModeloDia** | `modelo_dia` | `nome` (ex. "Dia útil", "Fim de semana"), `padrao` (bool), `usuario_id`, `versao` (`@Version`) |
| **BlocoModelo** | `bloco_modelo` | `modelo_dia_id`, `titulo`, `hora_inicio`, `hora_fim` (LocalTime), `cor`, `ordem`, `minutos_antecedencia_lembrete` (nullable), `versao` |
| **AtribuicaoDia** | `atribuicao_dia` | `usuario_id`, `dia_semana` (SEG…DOM), `modelo_dia_id` — mapeia semana → modelo |
| **Habito** | `habito` | `usuario_id`, `nome`, `icone`, `cor`, `tipo_agenda` (DIARIO \| DIAS_SEMANA \| VEZES_POR_SEMANA), `dias_semana`, `meta_semanal`, `hora_preferida`, `hora_lembrete`, `arquivado_em`, `versao` |
| **RegistroHabito** | `registro_habito` | `habito_id`, `data` (LocalDate), `status` (FEITO \| PULADO), `concluido_em` — **unique(habito_id, data)** |
| **Tarefa** | `tarefa` | `usuario_id`, `titulo`, `observacoes`, `prioridade` (BAIXA \| MEDIA \| ALTA), `minutos_estimados`, `data_limite`, `data_planejada`, `hora_planejada` (nullable), `bloco_planejado_id` (nullable), `status` (A_FAZER \| FEITA \| CANCELADA), `concluido_em`, `hora_lembrete`, `criado_em`, `versao` |
| **LembreteDia** | `lembrete_dia` | `usuario_id`, `disparar_em` (Instant / `timestamptz`), `tipo_origem` (BLOCO \| HABITO \| TAREFA \| RESUMO), `origem_id`, `titulo`, `corpo`, `status` (PENDENTE \| ENVIADO \| FALHOU \| CANCELADO), `tentativas`, `enviado_em` |
| **InscricaoPush** | `inscricao_push` | `usuario_id`, `endpoint`, `p256dh`, `auth`, `user_agent`, `visto_em`, `criado_em` |
| **RefreshToken** | `refresh_token` | `usuario_id`, `token_hash`, `familia`, `expira_em`, `revogado_em`, `criado_em` |

### Regras de derivação

- **"Dia de hoje" (`GET /api/v1/day?date=…`):** resolve o `AtribuicaoDia` do dia da semana →
  devolve os blocos com horário daquela data + os hábitos "devidos" naquela data (com status do
  `RegistroHabito`) + as tarefas com `data_planejada = data` (e as atrasadas: `data_planejada < hoje`
  e `status = A_FAZER`). Nada disso é persistido como "instância de dia".
- **`streak` (por hábito):** anda para trás nos dias em que o hábito era devido pela agenda vigente;
  `FEITO` conta, `PULADO` = descanso planejado (não quebra), dia devido sem log **quebra**. Para
  `VEZES_POR_SEMANA` (v1.1), conta em semanas que bateram a `meta_semanal`; semana corrente em aberto
  não quebra. Não é armazenado no MVP.
- **Materialização de lembrete:** ver seção 7.

### Fora do MVP no modelo

- `BlocoCompletion` (check-off de bloco por data) — v1.1.
- Overrides de um dia específico sem mexer no template — futuro.
- `valor_meta` / `valor_registrado` em hábito (meta numérica) — futuro. Adicionável sem quebra.
- Versionamento da agenda do hábito (hoje o `streak` recalcula pela agenda atual) — futuro.

---

## 6. Telas e fluxo de uso

**Navegação:** tab bar embaixo no celular / menu lateral no PC, com 4 abas:
**`Hoje` · `Resumo` · `Hábitos` · `Tarefas`**. `Rotina` e `Ajustes` ficam acessíveis pelo `Resumo`
e por um ícone de engrenagem no topo.

| Tela | Papel | Observações |
|---|---|---|
| **`Hoje`** | Execução: linha do tempo do dia, bloco "agora", marcar hábitos/tarefas | Barra **fina** de progresso no topo; seção "Atrasadas"; botão flutuante **`[ + ]`**; marcar item é **otimista com rollback** |
| **`Resumo`** | Dashboard: progresso do dia + visão da semana (streaks, hábitos X/total, tarefas concluídas) | Nasce "dia + semana"; alternância semana/mês fica para depois |
| **`Rotina`** | Montar `ModeloDia` (blocos, cores, horários) e atribuir modelo a cada dia da semana | Renomeada de "Semana" para não colidir com a visão semanal do `Resumo`. Tucada no `Resumo`/`Ajustes` |
| **`Hábitos`** | CRUD + histórico | Histórico em **lista** no MVP; grade estilo "contribuições" na v1.1 |
| **`Tarefas`** | Lista/backlog com filtros: **Hoje · Atrasadas · Sem data · Concluídas** | — |
| **`Nova tarefa`** | Tela dedicada (rota `/tarefas/nova`, **não** modal) com o formulário completo | O `[ + ]` de `Hoje` e de `Tarefas` leva para cá; aberto a partir de `Hoje` já vem com `dataPlanejada = hoje` |
| **`Ajustes`** | Perfil, **fuso horário**, notificações (permissão + instrução iOS + "enviar teste"), fallback (Telegram/e-mail), exportar JSON, sair | — |

### Layout do `Hoje` (rascunho)

```
┌─────────────────────────────────────┐
│ Sáb, 30 ago            [▓▓▓▓░░░] 58% │
├─────────────────────────────────────┤
│  AGORA · 09:00–12:00                 │
│  🎯 Foco no trabalho                 │
│  faltam 1h12 · próximo: Almoço 12:00 │
├─────────────────────────────────────┤
│  07:00  ✅ Acordar + água            │
│  07:30  ✅ Treino          🔥 12     │
│  09:00  🎯 Foco no trabalho (agora)  │
│  09:30  ☐ Responder e-mail cliente  │
│  12:00  ☐ Almoço                    │
│  14:00  ☐ Estudar Angular  🔥 4     │
│  19:00  ☐ Ler 20 min       🔥 0     │
├─────────────────────────────────────┤
│                              [ + ]  │
└─────────────────────────────────────┘
```

- Linha do tempo = blocos do `ModeloDia` do dia da semana + hábitos devidos + tarefas do dia
  (com `hora_planejada` aparecem no horário; sem horário, caem numa área "sem horário").
- Checkbox marca hábito/tarefa; swipe num hábito = "pular" (não quebra `streak`).
- Tocar num item abre um painel rápido (adiar, editar, mudar hora).

### Ciclo diário

1. *(Opcional)* notificação de "bom dia" com resumo do dia → abre em `Hoje`.
2. Ao longo do dia: lembretes por bloco/hábito/tarefa. Tocar na notificação cai direto no item.
3. O usuário marca conforme faz; a barra de progresso enche; o `streak` atualiza na hora.
4. Fim do dia: nada obrigatório (revisão noturna fora do MVP).

---

## 7. Notificações e lembretes

### Fluxo completo

1. **Ativar (cliente, em `Ajustes`).** No iOS, Web Push **só funciona com o PWA instalado na tela
   de início**. Se detectar iOS + não instalado → mostrar a instrução "Compartilhar → Adicionar à
   Tela de Início" antes de tudo. "Ativar notificações" → `Notification.requestPermission()` →
   `SwPush.requestSubscription({ serverPublicKey: VAPID_PUBLIC })` → `POST /api/v1/inscricoes-push`
   com `endpoint`, `p256dh`, `auth`, `user_agent`. Botão **"enviar teste"** →
   `POST /api/v1/inscricoes-push/testar`.
2. **Chaves VAPID** — par gerado uma vez, guardado como env var no backend; a pública vai para o front.
3. **Materialização (`LembreteDia`)** — job `@Scheduled` de hora em hora: para cada usuário, cria os
   `LembreteDia` das próximas 48h que ainda não existem (idempotente por
   `tipo_origem + origem_id + data`). Também gera sob demanda quando o cliente chama `GET /api/v1/day`.
   - **Bloco:** se `minutos_antecedencia_lembrete != null` → `disparar_em = inicio_do_bloco − antecedência`.
   - **Hábito** devido no dia com `hora_lembrete != null` → dispara nesse horário.
   - **Tarefa** com `data_planejada = dia` e `hora_lembrete != null` → dispara.
   - **Resumo "bom dia":** `tipo_origem = RESUMO`, horário configurável (default 07:00).
   - `disparar_em = ZonedDateTime.of(data, hora, ZoneId.of(usuario.fusoHorario)).toInstant()`.
4. **Envio (`@Scheduled(fixedDelay = 60s)`).** `WHERE status = PENDENTE AND disparar_em <= now()`.
   Revalida cada um: hábito/tarefa já concluído → `CANCELADO`; atraso > 30 min (servidor esteve
   fora) → `CANCELADO` (nudge atrasado é ruído). Envia via `web-push` para todas as `InscricaoPush`
   do usuário. Payload: `{ titulo, corpo, url: "/hoje?item=…" }`. Push retorna `404/410` → remove a
   `InscricaoPush`. Sucesso → `ENVIADO`. Falha transitória → continua `PENDENTE`; após N tentativas
   → `FALHOU`.
5. **Recebimento (service worker).** `push` → `showNotification(...)` com
   `tag = tipo_origem + origem_id` (não empilha duplicado); `notificationclick` → abre/foca o app
   na `url`.

### Fallback confiável

Interface `NotificacaoChannel` com implementações `PushWeb`, `Telegram`, `Email`. **No v1 entra só
`PushWeb`.** `Telegram` vem na v1.1 (bot API, sem custo, mais confiável que e-mail), como camada
extra para lembretes marcados como críticos.

### Limitações aceitas (explícito)

- iOS entrega Web Push via APNs → pontualidade costuma ser boa (segundos a poucos minutos), **mas**:
  o PWA precisa continuar instalado + permissão concedida; sem rede, o push chega quando a rede
  voltar; **todo push mostra notificação** (push silencioso faz o iOS revogar a permissão); não há
  som de despertador forte.
- O **backend precisa estar no ar** no horário → por isso o hosting é always-on (seção 11).
- Scheduler de 1 min → o lembrete pode sair até ~60s depois da hora. Aceitável para nudge.

### Fuso / horário de verão

`fuso_horario` IANA no `Usuario`; conversão sempre via `ZoneId`. Brasil hoje não tem horário de
verão, mas o código lida corretamente se voltar (`ZonedDateTime` resolve _gap_/_overlap_). Trocar o
fuso em `Ajustes` recalcula os `LembreteDia` ainda `PENDENTE`; a `data` histórica dos logs não muda.

---

## 8. Autenticação e sincronização

### Autenticação

- **E-mail + senha**, hash **BCrypt** (`PasswordEncoder` do Spring Security).
- **Registro fechado:** o endpoint existe mas exige `codigoConvite` igual a uma env var. O autor
  registra a conta uma vez e pode desabilitar depois. Sem porta aberta, sem semear usuário na mão.
- **Sessão:** `POST /api/v1/auth/login` → **access token JWT** curto (~20 min, em memória no front)
  + **refresh token** longo (~60 dias) em **cookie httpOnly + Secure + SameSite**. Em `401`, o front
  chama `/api/v1/auth/refresh` (cookie vai sozinho) → novo access token. Rotação de refresh +
  tabela `refresh_token` para permitir "sair de todos os dispositivos". **Grace de reuso de ~60s**
  para não deslogar quando várias abas dão refresh ao mesmo tempo.
- **Spring Security** stateless, filtro JWT, `/api/v1/auth/**` público, resto autenticado, CORS
  liberado só para a origem do front. Rate limit em `/api/v1/auth/login` (~10/min/IP).
- **"Esqueci a senha"** fica pós-MVP (depende de e-mail). Se necessário, reset via linha de comando.

### Sincronização

App cliente/servidor comum — **o Postgres é a fonte única da verdade**; todo dispositivo lê/escreve
pela API. Sem CRDT, sem offline-first no MVP.

- **Leitura:** `GET /api/v1/day?date=`, `/habitos`, `/tarefas?filtro=`… O cliente guarda a última
  resposta em **IndexedDB** (via `ngsw` dataGroups) → `Hoje` abre instantâneo e funciona em **modo
  leitura** se cair a rede.
- **Escrita:** assume online no MVP. Offline → UI mostra "sem conexão, tentando de novo". **Fila de
  reenvio offline fica explicitamente pós-MVP (v1.1).**
- **Concorrência (celular + PC):** _optimistic locking_ com coluna `versao` (`@Version`) em `Tarefa`,
  `Habito`, `ModeloDia`, `BlocoModelo`. Conflito → `409` → o cliente rebusca e reaplica.
  `RegistroHabito` é _upsert_ idempotente por `(habito_id, data)` → last-write-wins, sem UI de
  conflito.
- **Atualização:** ao voltar o foco na aba (`visibilitychange`) ou reconectar, rebusca o dia atual +
  contadores. Sem WebSocket no MVP.
- **"Hoje" é calculado no servidor** a partir de `usuario.fuso_horario` → celular e PC concordam
  mesmo se o relógio/fuso do PC estiver diferente.

### Exportar dados

`GET /api/v1/exportar` → um JSON único com rotina, hábitos, logs e tarefas. Rede de segurança e
portabilidade. Import fica pós-MVP.

---

## 9. Erros e casos de borda

**Notificações**
- Permissão negada/revogada → banner em `Hoje` "notificações desligadas, reativar". PWA não
  instalado no iOS → aviso explícito com o passo a passo.
- Assinatura expira (`404/410`) → backend remove; o front revalida e reinscreve ao abrir.
- Backend volta depois de fora do ar → a regra dos 30 min descarta os lembretes muito atrasados,
  envia só os recentes (sem chuva de notificação).
- Vários dispositivos → o mesmo lembrete vai para todos (desejado); `tag` evita empilhar no mesmo
  device.

**Fuso / datas**
- "Hoje" vira à meia-noite **no fuso do usuário**, nunca no do servidor.
- Mudar de fuso → recalcula `LembreteDia` `PENDENTE`; `data` histórica não muda.
- **Bloco cruzando a meia-noite (23:00–01:00) não é permitido no MVP** — valida `hora_fim > hora_inicio`.

**Rotina / blocos**
- Blocos sobrepostos → **permitido, com aviso** na tela `Rotina`. Não bloqueia.
- Dia da semana sem `AtribuicaoDia` → `Hoje` mostra "sem rotina para hoje" + atalho; hábitos e
  tarefas ainda aparecem.
- Deletar `ModeloDia` em uso → **bloqueado** ("em uso em Seg, Qua"). Deletar `BlocoModelo` → tarefas
  com `bloco_planejado_id` apontando para ele voltam a `null` (continuam no dia, sem bloco).

**Hábitos**
- Editar agenda afeta **só o futuro**; no MVP o `streak` recalcula pela agenda vigente (versionar
  agenda fica pós-MVP).
- Marcar em dia não-devido → conta como bônus, não penaliza `streak`.
- Arquivar → some dos ativos, histórico preservado, `streak` congela.
- Desmarcar → deleta a linha de `RegistroHabito`.

**Tarefas**
- Atrasada (`data_planejada < hoje`, `A_FAZER`) → seção "Atrasadas" no topo de `Hoje`, não some.
- `data_limite` vencida → badge vermelho, status não muda.
- Soma das estimativas do dia > espaços livres → **aviso leve** ("dia lotado, +2h além dos
  espaços"), não bloqueia.

**Auth / rede**
- Refresh expirado/revogado → tela de login preservando a rota de retorno.
- Marcar hábito/tarefa é **otimista com rollback**; o resto das escritas é pessimista.
- `5xx` → toast "algo deu errado" + tentar de novo; sem estado corrompido no cliente.

**Dados / validação**
- Bean Validation + `@ControllerAdvice` → `400` com `{ campo, mensagem }[]`.
- ID de outro usuário → `404` (não vaza existência).
- Limites de sanidade: título ≤ 200, observações ≤ 5000, nomes ≤ 100 → `422` se estourar.

**Versionamento / deploy**
- API sob `/api/v1` desde já. Deploy novo → `SwUpdate` avisa "nova versão, atualizar".
- HTTPS only, cookies `Secure`, HSTS.

---

## 10. Estratégia de testes

**Princípio:** `Clock` injetável no Spring e `TimeProvider` no Angular. Toda regra de negócio nova
tem teste; todo bug corrigido ganha teste de regressão. Sem meta cega de cobertura.

### Backend (JUnit 5 + AssertJ + Mockito)

- **Unit — onde mora o risco:** `StreakCalculator`, `DiaAssembler`, `LembreteGenerator`
  (`disparar_em` correto em `Instant`, casos de fuso/DST com clock fixo), `LembreteDispatcher`
  (regra dos 30 min, hábito já feito → `CANCELADO`, contador de tentativas). Table-driven.
- **Slice:** `@WebMvcTest` (formato do `400`, `401` sem token, `404` para id alheio, contrato JSON);
  `@DataJpaTest` (query de lembretes `PENDENTE` vencidos, unique de `RegistroHabito`, bump do
  `@Version`).
- **Integração:** `@SpringBootTest` + **Testcontainers PostgreSQL** (Flyway roda do zero → cobre
  "migração aplica limpo"). Fluxos completos: registrar → login → criar rotina → `GET /api/v1/day`
  → marcar hábito → `streak` sobe; refresh rotation + grace de reuso + logout revoga; lembrete
  ponta a ponta com `NotificacaoChannel` fake em memória e clock controlado (materializa → avança
  relógio → despacha → envia 1x, idempotente na 2ª rodada).
- Lib `web-push`: não se testa; mocka-se `NotificacaoChannel`.

### Frontend (Angular + Vitest + @testing-library/angular)

- Services de domínio: cálculo de "agora", encaixe de tarefas nos espaços livres, "dia lotado",
  formatação de `streak`.
- Guard de auth e **interceptor** (anexa access token; no `401` faz refresh e repete a request
  **uma vez**; se o refresh falha, desloga).
- Componentes: `Hoje` renderiza itens e faz rollback quando a marcação falha; `Nova tarefa` valida
  campos.
- Handler do service worker extraído para função pura (montar `showNotification`, escolher `tag`,
  rota do `notificationclick`) → testável em unit.
- Tipos TS gerados do **OpenAPI** (springdoc) → build quebra se o contrato divergir.

### E2E (Playwright — poucos, ~7, alto valor)

Contra backend real + front buildado:

1. Registrar com convite → login → `Hoje` vazio.
2. Criar `ModeloDia` com 2 blocos → atribuir a hoje → linha do tempo aparece.
3. Hábito diário → marcar → `streak` 1 → `Resumo` reflete.
4. Tarefa via `[ + ]` → aparece em `Hoje` → concluir → some dos pendentes.
5. Tarefa de ontem não feita → aparece em "Atrasadas".
6. Ativar notificações (permissão via contexto do Playwright) → "enviar teste" →
   `POST /api/v1/inscricoes-push` chamado.
7. Forçar `401` → interceptor faz refresh → request repete sem deslogar.

### CI — GitHub Actions

- Job backend: `mvn verify` (Docker no runner para o Testcontainers).
- Job frontend: `npm ci && npm run test:ci && npm run build`.
- Job e2e: sobe o jar + Postgres service container + serve o front → `playwright test` (só em PR
  para `main` e no merge).
- Gates de lint/format: `spotless` (Java) + `eslint` / `prettier` (front).

---

## 11. Infraestrutura e operação

### Por que precisa de servidor

O **front (PWA)** é só arquivo estático (pode ir de graça no Cloudflare Pages / GitHub Pages). O que
**precisa rodar 24/7** é:

- **O backend Spring Boot** — o agendador de lembretes (`@Scheduled` a cada 60s + materialização de
  hora em hora) tem que estar vivo para disparar o Web Push na hora certa.
- **O PostgreSQL** — fonte da verdade, sincroniza iPhone ↔ PC.

Ou seja: **1 processo pequeno always-on + 1 Postgres gerenciado**. É o único custo/operação
recorrente do projeto.

### Opções de hospedagem

| Opção | Custo | Observações |
|---|---|---|
| **Railway** (recomendado para começar) | ~US$ 5/mês | Deploy direto do GitHub, Postgres gerenciado, zero ops, sem scale-to-zero. |
| **Oracle Cloud "Always Free"** VM + docker-compose + Caddy | US$ 0 | Grátis de verdade e aguenta bem; o autor administra SO, TLS, backup. Bom para aprender. |
| **Fly.io** máquina 512MB + Fly Postgres | ~US$ 3–5/mês | Precisa **desligar** o scale-to-zero. `fly.toml` para configurar. |
| **Render** free | — | **Dorme por inatividade** → não serve para o agendador. Descartado. |

Notas: Spring Boot idle usa ~300–500MB RAM (escolher tier de 512MB; imagem nativa GraalVM para
reduzir isso fica como otimização futura, fora do MVP). HTTPS é obrigatório (PWA + Web Push) e todas
as opções acima fornecem de graça. Subdomínio grátis (`*.up.railway.app` / `*.fly.dev`) é
suficiente; domínio próprio (~US$ 10/ano) é opcional.

### GitHub / versionamento

- **1 repositório privado.** `main` protegida; trabalho em branches `feat/…`, PR para `main`, CI
  verde obrigatório para merge.
- **CD:** a plataforma (Railway/Fly) conecta no repo e faz deploy no merge para `main`; ou um step
  de GitHub Action (`railway up` / `flyctl deploy`).
- **Secrets** (chaves VAPID, segredo JWT, URL do banco, código de convite) → GitHub Actions secrets
  + variáveis de ambiente na plataforma. Nunca no repo. `.env.example` versionado.

### Confiabilidade ("todo dia, o dia todo")

- Plano **sem scale-to-zero** (desligar no Fly; não usar Render free).
- A plataforma reinicia o container em caso de crash; healthcheck em `/actuator/health`.
- **Backups:** Postgres gerenciado (Railway/Fly) tem backup diário automático nos planos pagos; mais
  o `GET /api/v1/exportar` como rede manual. Em VPS, `pg_dump` noturno para outro disco/objeto.
- **Monitoramento:** ping de uptime grátis (UptimeRobot / BetterStack free) em `/actuator/health` a
  cada 5 min → e-mail se cair.
- **Custo de ficar fora do ar:** perdem-se os lembretes daquela janela; os dados ficam seguros no
  Postgres; a regra dos 30 min evita enxurrada de notificações na volta.

A escolha final da hospedagem **só bloqueia na Fatia 8 (deploy)** — as Fatias 1–7 rodam localmente.

---

## 12. Escopo do MVP (v1)

### Entra no v1

- **Base:** repositório único, Spring Boot (Maven, Java 21) + PostgreSQL (docker-compose local) +
  Flyway, Angular PWA, CI de build. Auth completa (registro com código de convite, login, refresh em
  cookie httpOnly, logout). 1 usuário. `fuso_horario` editável em `Ajustes`.
- **Rotina:** CRUD de `ModeloDia` + `BlocoModelo`; atribuir modelo aos 7 dias; validação de
  meia-noite; aviso de sobreposição.
- **Hábitos:** CRUD. Agendas **`DIARIO` e `DIAS_SEMANA`** (`VEZES_POR_SEMANA` fica para v1.1).
  Marcar `FEITO` / `PULAR`. `streak`. Histórico em **lista**.
- **Tarefas:** CRUD pela tela `Nova tarefa` + botão `[ + ]`. Campos: título, observações,
  prioridade, estimativa, `data_limite`, `data_planejada`, `hora_planejada`, `hora_lembrete`.
  Filtros Hoje / Atrasadas / Sem data / Concluídas. No `Hoje`, tarefa com `hora_planejada` aparece
  no horário; sem horário, cai em área "sem horário". (Sugestão automática de espaço livre = v1.1.)
- **Hoje:** linha do tempo (blocos + hábitos devidos + tarefas), bloco "agora", barra fina de
  progresso, seção "Atrasadas", marcar itens (otimista com rollback).
- **Resumo:** progresso do dia + visão simples da semana (streaks, hábitos X/total, tarefas
  concluídas). Sem toggle semana/mês. `Rotina` acessível daqui e de `Ajustes`.
- **Notificações:** só **Web Push** (`PushWeb`). Ativar/permissão/instrução iOS/"enviar teste" em
  `Ajustes`. Materialização + despacho (`@Scheduled`). Lembrete de bloco, hábito e tarefa. Resumo
  "bom dia" opcional com horário configurável.
- **PWA + entrega:** manifest, ícones, `ngsw`, instalável, cache de leitura offline do dia,
  `SwUpdate`. Deploy backend + Postgres always-on, front estático, HTTPS. `GET /api/v1/exportar`.
  Rate limit no login.

### Fora do v1

- **v1.1:** canal **Telegram** + lembrete "crítico"; `VEZES_POR_SEMANA`; grade de histórico do
  hábito; sugestão de encaixe em espaço livre + aviso "dia lotado"; check-off de bloco; fila de
  reenvio offline.
- **Futuro:** toggle semana/mês + gráficos no `Resumo`; overrides de um dia sem mexer no template;
  revisão noturna; hábito com meta numérica; "esqueci a senha" + e-mail; multiusuário de verdade;
  widgets/Siri (só migrando para o Caminho B — reavaliar); App Store/TestFlight; imagem nativa
  GraalVM.

### Ordem de construção (fatias verticais — cada uma entrega algo usável)

| # | Fatia | Critério de "pronto" |
|---|---|---|
| 1 | Fundação: esqueletos back + front + Postgres + Flyway V1 (todas as tabelas) + CI | Os dois compilam no CI; app sobe local |
| 2 | Auth completa | Consigo logar e ver tela protegida vazia |
| 3 | Rotina + `Hoje` (esqueleto) | Monto minha rotina e vejo a linha do tempo do dia |
| 4 | Hábitos + `streak` | Marco um hábito 3 dias e vejo `streak` 3 |
| 5 | Tarefas | Crio tarefa pelo `[ + ]`, encaixo no dia, concluo |
| 6 | `Resumo` | Dashboard dia + semana funcionando |
| 7 | Notificações | Recebo lembrete no iPhone no horário |
| 8 | PWA polish + deploy | App instalado na tela de início do iPhone, sincronizando com o PC |
| 9 | Exportar + endurecimento (rate limit, e2e no CI, revisão de bordas) | Tudo verde, dados exportáveis |

Cada fatia vira seu próprio ciclo de plano → implementação. A **Fatia 1** é a próxima a detalhar.

---

## 13. Decisões em aberto

1. **Hospedagem final** (Railway vs Oracle Always Free vs Fly.io) — confirmar na Fatia 8.
2. **Horário default do resumo "bom dia"** — proposto 07:00.
3. **`Nova tarefa` no desktop:** rota cheia (como no mobile) ou painel lateral — reavaliar UX na
   Fatia 5.

### Decisões fechadas depois da versão inicial

- **Nome do repositório:** `APP-TASKY` (projeto **Tasky**) — fechado em 2026-08-31.
- **Runner de teste no front:** **Vitest**, não Jest — fechado em 2026-08-31 (ver seção 15).

---

## 14. Glossário

- **PWA (Progressive Web App):** aplicação web instalável na tela de início, com service worker.
- **`streak`:** número de dias (ou semanas) consecutivos em que um hábito foi cumprido.
- **Bloco:** intervalo de tempo nomeado dentro de um `ModeloDia` (ex.: "Foco no trabalho" 09:00–12:00).
- **`ModeloDia`:** agenda-modelo reutilizável, composta de blocos, atribuível a dias da semana.
- **Nudge:** lembrete leve (não é alarme); tolera atraso de alguns minutos.
- **VAPID:** esquema de chaves que autentica o servidor perante o serviço de push do navegador.
- **Web Push:** padrão do navegador para receber notificações com o app fechado; no iOS, via APNs,
  só para PWA instalado.
- **Service worker:** script que roda em segundo plano no navegador; recebe o push e mostra a
  notificação.
- **Materialização de lembrete:** transformar as regras (blocos/hábitos/tarefas + fuso) em linhas
  concretas de `LembreteDia` com horário absoluto (`Instant`).

---

## 15. Revisão de versões e riscos (2026-08-31)

Ao preparar a Fatia 1, as versões reais do ecossistema foram verificadas e algumas escolhas da
versão inicial deste documento foram atualizadas.

### Versões alvo confirmadas

| Item | Versão | Observação |
|---|---|---|
| **Spring Boot** | **4.1.1** | Estável atual (`4.2.0-M1` é milestone, ignorado). O documento original dizia "Spring Boot 3". |
| **Java** | **21** (Temurin) | Baseline do Boot 4.1.1 é Java 17 → **a escolha por Java 21 continua válida**. |
| **Maven** | 3.9.16 | Já instalado na máquina do autor. |
| **Angular / CLI** | **22.1.6** | Exige Node `^22.22.3 \|\| ^24.15.0 \|\| >=26.0.0`. |
| **Node** | **24.20.0 LTS** | A máquina tinha Node 20 (insuficiente). Gerenciado por `nvm-windows`. |
| **TypeScript** | 6.0.x | Faixa exigida pelo `@angular/build` 22 (`>=6.0 <6.1`). |
| **Vitest** | 4.0.x | Peer dependency de `@angular/build` 22. |
| **springdoc-openapi** | 3.1.0 | A linha 3.x é a compatível com Spring Boot 4 (a 2.x era para o Boot 3). |
| **PostgreSQL** | pinado no `docker-compose` | Versão fixada explicitamente, sem tag flutuante. |

### Mudança: Jest → Vitest

A versão inicial propunha **Jest** como runner do front. Verificando o Angular 22, o
`@angular/build` traz **`vitest ^4.0.8`** como peer dependency e o **Karma está depreciado** desde o
Angular 20. **Vitest** passa a ser o runner do projeto — é o caminho oficial e evita montar
integração customizada. Aprovado pelo autor em 2026-08-31.

### Risco aberto: biblioteca de Web Push

`nl.martijndwars:web-push` está em **5.1.2, sem release desde fevereiro de 2025** (~18 meses).
Não foi validada contra Java 21 / Spring Boot 4.

- **Quando morde:** só na **Fatia 7** (notificações). Não bloqueia as Fatias 1–6.
- **Contenção já prevista no design:** a lib fica atrás da interface `NotificacaoChannel`, então
  trocá-la não afeta o resto do sistema.
- **Plano B:** implementar o protocolo Web Push diretamente (ECDH P-256 + HKDF + AES128GCM +
  cabeçalhos VAPID assinados com ES256), usando BouncyCastle ou a JCA do próprio JDK. É código
  autocontido de porte pequeno.
- **Ação:** validar a lib com um _spike_ logo no início da Fatia 7, antes de construir a UI de
  notificações em cima dela.

### Pré-requisitos de máquina (Windows 11)

Levantados em 2026-08-31: **Maven 3.9.16** já presente; **JDK 21**, **Node 24 LTS**, **WSL2** e
**Docker Desktop** precisavam ser instalados. O Node é gerenciado por `nvm-windows` (não usar
Chocolatey para ele). Docker é necessário tanto para o PostgreSQL local quanto para os testes com
Testcontainers.
