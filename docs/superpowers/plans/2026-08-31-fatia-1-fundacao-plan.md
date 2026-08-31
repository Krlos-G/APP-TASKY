# Plano de Implementação — Fatia 1: Fundação

- **Data:** 2026-08-31
- **Projeto:** Tasky — https://github.com/Krlos-G/APP-TASKY
- **Spec de referência:** `docs/superpowers/specs/2026-08-30-app-rotina-pessoal-design.md`
- **Status:** aguardando execução

---

## Objetivo da fatia

Montar o esqueleto do monorepo com backend, frontend e CI funcionando, e o **schema completo do
banco** já versionado no Flyway.

**Critério de pronto (Definition of Done):**

1. `docker compose up -d` sobe o PostgreSQL.
2. `mvn verify` no `/backend` passa, incluindo um teste de integração com Testcontainers que roda
   as migrações do Flyway do zero contra um PostgreSQL real.
3. `npm run build` e `npm test` no `/frontend` passam.
4. O app abre no navegador e navega entre as 4 telas vazias (`Hoje`, `Resumo`, `Hábitos`, `Tarefas`).
5. O CI do GitHub Actions fica verde em um PR.
6. O `README.md` permite a outra pessoa subir o projeto do zero.

**Fora do escopo desta fatia:** autenticação (Fatia 2), qualquer regra de negócio, qualquer
endpoint de domínio, qualquer tela com conteúdo real.

---

## Etapa 0 — Pré-requisitos de máquina

> **Contexto importante:** esta é uma máquina corporativa que também roda uma **aplicação legado**
> (Java 8 + Angular/Node 20). Nada aqui pode quebrar aquele ambiente. O levantamento de 2026-08-31
> mostrou que a convivência é tranquila — inclusive o JDK 21 já estava presente.

| Ferramenta | Alvo | Situação em 2026-08-31 | Convive com o legado? |
|---|---|---|---|
| Maven | 3.9.16 | ✅ já instalado | sim |
| **JDK 21** | `ms-21.0.10` em `~/.jdks` | ✅ **já presente** — só faltava `JAVA_HOME` | sim, ver nota A |
| Node | 24.20.0 LTS | ⚠️ havia só 20.20.0 no `nvm` | sim, ver nota B |
| WSL2 | — | ❌ instalar | sim, ver nota C |
| Docker Desktop | 4.88.x | ❌ instalar | sim, ver nota D |

**Nota A — Java.** A máquina já tinha os JDKs do IntelliJ em `C:\Users\<user>\.jdks\`:
`corretto-1.8.0_502` (usado pelo legado) e `ms-21.0.8/.9/.10`. **Nenhuma instalação foi
necessária** — bastou definir `JAVA_HOME` para o `ms-21.0.10` no escopo de usuário. O IntelliJ fixa
o JDK **por projeto**, então o legado permanece no Corretto 8 independentemente do `JAVA_HOME`.
Ressalva: compilar o legado **pelo terminal** passaria a usar o Java 21 — nesse caso, apontar o
`JAVA_HOME` para o Corretto 8 apenas naquela sessão.

**Nota B — Node.** O `nvm-windows` alterna versões **globalmente** (troca um symlink em
`C:\nvm4w\nodejs`), e não por terminal como o nvm de Linux/macOS. Portanto:

- `nvm install 24.20.0` apenas adiciona a versão — **não muda nada**.
- `nvm use 24.20.0` passa a valer para **todos** os terminais, inclusive os do legado.
- Voltar ao ambiente de trabalho: `nvm use 20.20.0`.
- Pacotes npm globais são **isolados por versão**, então os CLIs do legado continuam intactos
  no 20.20.0. O Tasky usa dependências locais (`devDependencies`), reduzindo a necessidade de
  globais.

**Nota C — WSL2.** A virtualização já estava habilitada (`HypervisorPresent = True`) e não há
VirtualBox nem VMware instalados, portanto sem risco de conflito de hipervisor. Não afeta Node,
Java nem Angular.

**Nota D — Docker Desktop.** Não interfere em versões de linguagem. **Atenção de licenciamento:**
o Docker Desktop exige assinatura paga para empresas com mais de 250 funcionários ou mais de
US$ 10M de faturamento — o critério é o porte da empresa, não a permissão individual. Alternativas
gratuitas equivalentes, caso seja necessário trocar depois: Podman Desktop, Rancher Desktop ou
Docker CE instalado dentro do WSL2. Qualquer uma delas atende ao PostgreSQL local e ao
Testcontainers.

**Verificação final (em um terminal novo):**

```
java -version   → 21.0.10
mvn -v          → 3.9.16, apontando para o JDK 21
node -v         → v24.20.0
docker info     → responde com uma versão de servidor
```

---

## Etapa 1 — Estrutura do repositório

```
APP-TASKY/
├─ .github/workflows/ci.yml
├─ .gitignore
├─ .env.example
├─ docker-compose.yml
├─ README.md
├─ docs/superpowers/
│  ├─ specs/2026-08-30-app-rotina-pessoal-design.md
│  └─ plans/2026-08-31-fatia-1-fundacao-plan.md
├─ backend/
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/br/com/tasky/…
│     ├─ main/resources/
│     │  ├─ application.yml
│     │  └─ db/migration/V1__esquema_inicial.sql
│     └─ test/java/br/com/tasky/…
└─ frontend/
   ├─ package.json
   └─ src/app/…
```

- **Pacote base:** `br.com.tasky`.
- **`.gitignore`:** cobrindo `target/`, `node_modules/`, `dist/`, `.env`, `.angular/`, `*.log`,
  arquivos de IDE.
- **`.env.example`:** versionado, com as chaves sem valores reais (`POSTGRES_USER`,
  `POSTGRES_PASSWORD`, `POSTGRES_DB`). O `.env` real **nunca** é commitado.

**Verificação:** `git status` limpo depois de um build completo — nenhum artefato ou segredo
aparecendo como não rastreado.

---

## Etapa 2 — PostgreSQL local via Docker Compose

`docker-compose.yml` com um único serviço:

- Imagem do PostgreSQL com **tag de versão fixa** (sem `latest`).
- Variáveis vindas do `.env`; porta publicada em `5432`.
- Volume nomeado para os dados persistirem entre `up`/`down`.
- `healthcheck` com `pg_isready`.

**Verificação:** `docker compose up -d` e o healthcheck fica `healthy`; conexão pelo cliente na
porta 5432 funciona.

---

## Etapa 3 — Esqueleto do backend

Projeto Maven, Spring Boot **4.1.1**, Java **21**.

**Dependências desta fatia:**

| Dependência | Para quê |
|---|---|
| `spring-boot-starter-web` | API REST |
| `spring-boot-starter-data-jpa` | Hibernate / repositórios |
| `spring-boot-starter-validation` | Bean Validation |
| `spring-boot-starter-actuator` | `/actuator/health` |
| `flyway-core` + `flyway-database-postgresql` | migrações |
| `postgresql` (runtime) | driver JDBC |
| `springdoc-openapi-starter-webmvc-ui` 3.1.0 | OpenAPI (gera os tipos do front depois) |
| `spring-boot-starter-test`, `spring-boot-testcontainers`, `testcontainers:postgresql` | testes |

> **Spring Security fica de fora nesta fatia, de propósito.** Com ele no classpath tudo já nasceria
> trancado, sem que exista login para destrancar. Entra na Fatia 2, junto com a autenticação.

**`application.yml`:**

- `spring.jpa.hibernate.ddl-auto: validate` — o Flyway é a única fonte do schema; o Hibernate só
  confere se as entidades batem com as tabelas.
- `spring.jpa.open-in-view: false`.
- Datasource lido de variáveis de ambiente, com default apontando para o compose local.
- Actuator expondo somente `health`.

**Verificação:** `mvn -q compile` passa; a aplicação sobe conectada ao Postgres do compose.

---

## Etapa 4 — Migração Flyway `V1__esquema_inicial.sql`

Cria **todas as 10 tabelas** do spec de uma vez.

| Tabela | Pontos de atenção |
|---|---|
| `usuario` | `email` único; `fuso_horario` obrigatório |
| `modelo_dia` | FK para `usuario`; coluna `versao` |
| `bloco_modelo` | FK para `modelo_dia`; **`CHECK (hora_fim > hora_inicio)`** |
| `atribuicao_dia` | **único `(usuario_id, dia_semana)`** |
| `habito` | `dias_semana` como texto curto (ex.: `"SEG,QUA,SEX"`) |
| `registro_habito` | **único `(habito_id, data)`** |
| `tarefa` | FK opcional para `bloco_modelo` com **`ON DELETE SET NULL`** |
| `lembrete_dia` | **índice `(status, disparar_em)`** — é a query do despachante |
| `inscricao_push` | `endpoint` único |
| `refresh_token` | `token_hash` único; índice por `usuario_id` |

**Convenções:**

- Tipos: `timestamptz` para `Instant`, `date` para `LocalDate`, `time` para `LocalTime`.
- **Enums como `varchar` + `CHECK`**, não tipos ENUM do PostgreSQL — evoluir um `CHECK` é muito mais
  simples do que alterar um tipo ENUM.
- Nomes de constraints explícitos (`uk_…`, `fk_…`, `ck_…`, `ix_…`) para que erros do banco sejam
  legíveis.
- `dias_semana` gravado como lista separada por vírgula, lida por um `AttributeConverter` do JPA.
  Escolhido em vez de tabela de junção ou array nativo por ser um conjunto pequeno e fixo; se um dia
  precisar de consulta por dia da semana, vira tabela de junção.

> **Sobre editar o `V1` depois:** enquanto não existir banco em produção, dá para alterar essa
> migração livremente (basta recriar o volume local). Depois do primeiro deploy real (Fatia 8),
> toda mudança passa a ser `V2`, `V3`, …

**Verificação:** com o banco zerado, a aplicação sobe e o Flyway aplica a `V1` sem erro; a tabela
`flyway_schema_history` registra a migração como bem-sucedida.

---

## Etapa 5 — Entidades JPA

Uma classe por tabela, nomes em português conforme a convenção do spec, com `@Table`/`@Column`
mapeando para o `snake_case`.

- Enums Java (`StatusTarefa`, `StatusRegistroHabito`, `TipoAgenda`, `Prioridade`, `DiaSemana`,
  `TipoOrigemLembrete`, `StatusLembrete`) com `@Enumerated(EnumType.STRING)`.
- `@Version` em `Tarefa`, `Habito`, `ModeloDia` e `BlocoModelo` (bloqueio otimista do spec).
- Um `RepositoryJpa` vazio por agregado, só para o contexto validar os mapeamentos.

Como o `ddl-auto` é `validate`, **qualquer divergência entre entidade e tabela quebra o boot** — é
exatamente o alarme que se quer nesta fatia.

**Verificação:** o contexto Spring sobe sem erro de validação de schema.

---

## Etapa 6 — `Clock` e health

- `@Bean Clock clock()` retornando `Clock.systemUTC()`, injetado em todo lugar que precisar de
  "agora". Nada de `Instant.now()` solto — é o que torna testável a lógica de lembretes e de
  virada de dia.
- `/actuator/health` respondendo `UP`.

**Verificação:** `curl` no health retorna `{"status":"UP"}`.

---

## Etapa 7 — Teste de integração com Testcontainers

Um teste `@SpringBootTest` que:

1. Sobe um container PostgreSQL real (mesma versão do compose), conectado via `@ServiceConnection`.
2. Deixa o Flyway aplicar a `V1` do zero.
3. Afirma que as **10 tabelas existem** consultando o catálogo do banco.
4. Afirma que o contexto Spring carregou (portanto, o `validate` do Hibernate passou).

Esse único teste cobre, de uma vez, "a migração aplica limpo" e "as entidades batem com o schema" —
as duas coisas que mais quebram silenciosamente numa fundação.

**Verificação:** `mvn verify` verde, com o Docker rodando.

---

## Etapa 8 — Esqueleto do frontend

- `ng new` com Angular **22**: roteamento habilitado, **SCSS**, SSR desabilitado, **Vitest** como
  runner (os flags exatos são confirmados na hora, contra a CLI instalada).
- `ng add @angular/pwa` → manifest, ícones e `ngsw-config.json`.
- **Shell da aplicação** com as 4 rotas vazias: `/hoje` (rota padrão), `/resumo`, `/habitos`,
  `/tarefas`. Cada uma é um componente standalone com apenas um título.
- **Navegação responsiva:** tab bar fixa embaixo em telas estreitas; navegação lateral no desktop.
  Um único componente decidindo por CSS, sem duplicar markup.
- `environment.ts` com a URL base da API; proxy de desenvolvimento apontando para o backend local.
- Um teste de exemplo passando (garante que o Vitest está de fato configurado).

> O service worker fica **desabilitado em desenvolvimento** (padrão do Angular) e só é exercitado
> de verdade na Fatia 8.

**Verificação:** `npm run build` e `npm test` passam; no navegador, as 4 telas navegam e a
navegação muda de formato ao estreitar a janela.

---

## Etapa 9 — CI no GitHub Actions

Arquivo `.github/workflows/ci.yml`, disparando em push para `main` e em pull request.

| Job | Passos |
|---|---|
| `backend` | checkout → JDK 21 (Temurin) com cache do Maven → `mvn -B verify` (o runner já tem Docker, então o Testcontainers funciona) |
| `frontend` | checkout → Node 24 com cache do npm → `npm ci` → `npm test` → `npm run build` |

Os dois jobs rodam em paralelo. Lint/format (`spotless`, `eslint`) ficam para uma fatia posterior,
para não travar a fundação.

**Verificação:** abrir um PR com esta fatia e ver os dois checks verdes.

---

## Etapa 10 — README

Conteúdo mínimo: o que é o Tasky (2 linhas), stack e versões, pré-requisitos, como subir
(`docker compose up -d` → backend → frontend), como rodar os testes, e a estrutura de pastas.

**Verificação:** seguir o próprio README numa pasta limpa e conseguir subir tudo.

---

## Ordem de execução e pontos de parada

```
0. Pré-requisitos (autor)
        ↓
1. Estrutura + .gitignore + .env.example
        ↓
2. docker-compose (Postgres) ──── ponto de parada: banco sobe
        ↓
3. Backend Maven + application.yml
        ↓
4. Flyway V1 (todas as tabelas)
        ↓
5. Entidades JPA + repositórios
        ↓
6. Clock + health ─────────────── ponto de parada: backend sobe e conecta
        ↓
7. Teste Testcontainers ───────── ponto de parada: mvn verify verde
        ↓
8. Frontend Angular + PWA + shell  ponto de parada: 4 telas navegam
        ↓
9. CI GitHub Actions ──────────── ponto de parada: checks verdes no PR
        ↓
10. README ────────────────────── FATIA 1 CONCLUÍDA
```

Cada ponto de parada é um lugar seguro para revisar, commitar e continuar depois.

**Estratégia de branch:** trabalhar em `feat/fatia-1-fundacao`, abrir PR para `main`, merge só com
o CI verde. Commits pequenos por etapa, não um commit gigante no fim.

---

## Riscos desta fatia

| Risco | Probabilidade | Contenção |
|---|---|---|
| Docker Desktop com problema no WSL2 (comum no Windows) | média | Resolver na Etapa 0. Alternativa temporária: PostgreSQL nativo para desenvolver, mas o Testcontainers **exige** Docker — não dá para pular. |
| Flags do `ng new` diferentes do esperado no Angular 22 | média | Confirmar rodando `ng new --help` contra a CLI instalada, em vez de assumir. |
| `ddl-auto: validate` reprovando por detalhe de tipo (ex.: `timestamptz` vs `timestamp`) | média | É o comportamento desejado — o erro aponta a coluna exata. Ajustar a migração, não afrouxar o `validate`. |
| Testcontainers lento na primeira execução (baixa a imagem) | alta | Só na primeira vez; a imagem fica em cache local e no cache do CI. |
| Spring Boot 4 com documentação/exemplos ainda escassos frente ao Boot 3 | média | Fatia 1 usa só o caminho principal do framework, onde a mudança 3→4 quase não aparece. |

---

## Próxima fatia

**Fatia 2 — Autenticação:** Spring Security, registro com código de convite, login, JWT de acesso,
refresh em cookie httpOnly com rotação, guard e interceptor no Angular, tela de login.
