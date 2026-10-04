# Tasky

App pessoal de rotina, hábitos e tarefas — usado no iPhone (PWA instalado na tela de início) e no
PC, sincronizados. Nasceu para ajudar a montar e, principalmente, a **seguir** uma rotina.

## Stack

| Camada | Tecnologia |
|---|---|
| Front | Angular 22 (PWA, zone.js, SCSS), TypeScript 6, Vitest |
| Back | Spring Boot 4.1.1, Java 21, Maven |
| Banco | PostgreSQL 18.6, migrações com Flyway |
| Notificações | Web Push (VAPID) |
| Hospedagem | Railway: app e API num contêiner só, mais o PostgreSQL |

## Pré-requisitos

- **JDK 21** com `JAVA_HOME` configurado
- **Maven 3.9+** (ou use o `mvnw` que acompanha o projeto)
- **Node 24 LTS** e npm
- **Docker** rodando (necessário para o banco, para o backend e para os testes com Testcontainers)

Confira tudo de uma vez:

```
java -version && mvn -v && node -v && docker info --format '{{.ServerVersion}}'
```

## Subindo o projeto

**1. Configure o ambiente** (só na primeira vez):

```
cp .env.example .env
```

Edite o `.env` e defina uma senha para o PostgreSQL. Esse arquivo não é versionado.

**2. Suba o banco e o backend:**

```
docker compose up -d
```

Isso sobe o PostgreSQL (porta **5433** no host) e o backend (porta **8080**). O Flyway aplica as
migrações automaticamente. A primeira subida demora alguns minutos, baixando as dependências Maven;
as seguintes são rápidas, porque o repositório fica num volume.

Acompanhe com `docker compose logs -f backend`.

> **Por que o backend roda em container também no desenvolvimento?** Em máquinas com software de
> segurança que intercepta rede (Kaspersky, FortiClient e similares), o `java.exe` pode ser impedido
> de abrir conexões de loopback — e o Tomcat não sobe, falhando com *"Unable to establish loopback
> connection"*. Dentro do container a rede é Linux e o problema não existe. Onde o Java funciona
> normalmente, `cd backend && ./mvnw spring-boot:run` continua sendo uma alternativa válida, desde
> que o `SPRING_DATASOURCE_URL` aponte para `localhost:5433`.

**3. Suba o frontend** (porta 4200):

```
cd frontend && npm install && npm start
```

Abra <http://localhost:4200>. O `ng serve` já encaminha as chamadas `/api` para o backend
(`proxy.conf.json`), então não há problema de CORS em desenvolvimento.

## Testes

```
cd backend && ./mvnw verify
```

```
cd frontend && npm run test:ci
```

Os testes do backend sobem um PostgreSQL real via Testcontainers — o Docker precisa estar rodando.

## Comandos úteis

| Comando | O que faz |
|---|---|
| `docker compose logs -f backend` | Acompanha o log do backend |
| `docker compose restart backend` | Reinicia só o backend |
| `docker compose down` | Para tudo, preservando os dados |
| `docker compose down -v` | Para tudo e **apaga** os dados (recria o schema do zero na próxima subida) |
| `cd frontend && npm run build` | Build de produção em `frontend/dist/` |
| `sh frontend/icones/gerar.sh` | Regera os ícones do PWA a partir dos SVGs (precisa do ImageMagick) |

Com o backend no ar, a documentação da API fica em <http://localhost:8080/swagger-ui.html> e o
health check em <http://localhost:8080/actuator/health>.

## Produção

O Tasky roda no **Railway**, em <https://app-tasky-production.up.railway.app>. Um contêiner só
serve o app e a API no mesmo endereço — de propósito: o Safari bloqueia cookie de terceiro, e o
cookie de renovação da sessão viraria um se o app e a API morassem em domínios diferentes.

**Deploy:** o Railway constrói o `Dockerfile` da raiz a cada push na `main`, depois que o CI passa
("Wait for CI"). O front é compilado dentro da imagem e servido pelo Spring.

**Ensaio local da imagem**, antes de mexer em algo de infraestrutura:

```
docker build -t tasky .
docker run --rm -p 8090:8080 --network app-iphone_default --env-file .env -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/tasky tasky
```

Abra <http://localhost:8090> — app e API numa porta só, como em produção.

### Configuração no painel do Railway

Fica no painel, e não em arquivo: o `railway.json` foi descontinuado (para de funcionar em
01/12/2026), e o substituto exige o CLI do Railway com credenciais a cada mudança.

| Serviço | Ajuste |
|---|---|
| `Postgres` | Região US East (Virginia) |
| app | Região US East; branch `main` com *Wait for CI*; healthcheck em `/actuator/health`; reinício *On Failure*; **Serverless desligado** — app adormecido não dispara lembrete; domínio na porta 8080 |

### Variáveis de produção (serviço do app)

| Variável | Valor |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `SPRING_DATASOURCE_USERNAME` | `${{Postgres.PGUSER}}` |
| `SPRING_DATASOURCE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `TASKY_JWT_SECRET` | segredo próprio de produção, 48 bytes aleatórios |
| `TASKY_VAPID_PUBLICA` / `TASKY_VAPID_PRIVADA` | par próprio de produção (as chaves de desenvolvimento nunca vão para lá) |
| `TASKY_VAPID_ASSUNTO` | `https://app-tasky-production.up.railway.app` |
| `TASKY_COOKIE_SECURE` | `true` |
| `TASKY_COOKIE_SAMESITE` | `Lax` |
| `SPRINGDOC_API_DOCS_ENABLED` / `SPRINGDOC_SWAGGER_UI_ENABLED` | `false` |
| `TASKY_CODIGO_CONVITE` | **ausente** — só existe enquanto se cria uma conta |

Gerar o segredo JWT e o par VAPID, sem baixar nada:

```
node -e "const c=require('crypto');const e=c.createECDH('prime256v1');e.generateKeys();console.log('TASKY_JWT_SECRET='+c.randomBytes(48).toString('base64'));console.log('TASKY_VAPID_PUBLICA='+e.getPublicKey('base64url'));console.log('TASKY_VAPID_PRIVADA='+e.getPrivateKey('base64url'))"
```

Trocar o par VAPID invalida as inscrições dos aparelhos: cada um precisa ativar as notificações de
novo.

### Criar uma conta

Não há tela de cadastro — o app é pessoal. Defina `TASKY_CODIGO_CONVITE` no Railway, faça o
deploy, cadastre pela API e **apague a variável** em seguida, o que fecha o cadastro:

```
curl -X POST https://app-tasky-production.up.railway.app/api/v1/auth/registrar -H "Content-Type: application/json" -d '{"email":"voce@exemplo.com","senha":"no minimo 12 caracteres","nomeExibicao":"Seu nome","codigoConvite":"o-codigo","fusoHorario":"America/Sao_Paulo"}'
```

### Backup manual do banco

O plano Hobby do Railway não faz backup automático do volume. Para uma cópia: no serviço
`Postgres`, ligue *Public Networking*, copie a `DATABASE_PUBLIC_URL` e rode

```
docker run --rm -v "${PWD}:/backup" postgres:18-alpine pg_dump "<DATABASE_PUBLIC_URL>" -Fc -f /backup/tasky-AAAA-MM-DD.dump
```

(O arquivo é gravado pela pasta montada, e não com `>`: no PowerShell o redirecionamento
corrompe saída binária.)

e **desligue o Public Networking** de novo — o banco não deve ficar exposto. Restaurar é o
caminho inverso, com `pg_restore`.

### iPhone

Instalar pelo **Safari** → Compartilhar → Adicionar à Tela de Início, com **Abrir como App Web**
ligado. Mudança no `index.html` que o iOS lê na instalação (como o estilo da barra de status) só
aparece reinstalando — e limpando antes os dados do site no Safari (Ajustes → Apps → Safari →
Avançado → Dados dos Sites), porque o service worker pode servir a página antiga.

## Estrutura

```
.
├─ backend/                     Spring Boot
│  └─ src/main/
│     ├─ java/br/com/tasky/
│     │  ├─ config/             configuração e beans de infraestrutura
│     │  ├─ entity/             entidades JPA, enums e converters
│     │  ├─ repository/         repositórios Spring Data
│     │  └─ security/           autenticação: tokens, filtros, políticas
│     └─ resources/
│        ├─ application.yml
│        └─ db/migration/       migrações Flyway
├─ frontend/                    Angular PWA
│  ├─ icones/                   SVGs dos ícones e o script que gera os PNGs
│  └─ src/app/
│     ├─ paginas/               uma pasta por tela
│     └─ app.routes.ts
├─ docs/superpowers/
│  ├─ specs/                    documento de design
│  └─ plans/                    planos de implementação por fatia
├─ Dockerfile                   imagem de produção: app e API num processo só
└─ docker-compose.yml           PostgreSQL e backend de desenvolvimento
```

## Convenções

- **Nomes de domínio em português**: tabelas e colunas em `snake_case` (`bloco_modelo`,
  `hora_inicio`), classes em `PascalCase` (`BlocoModelo`), enums em maiúsculas (`A_FAZER`).
  Exceção deliberada: `streak` fica em inglês.
- **Sufixos técnicos em inglês**, seguindo a convenção Spring: `UsuarioRepository`,
  `RefreshTokenService`, `AuthProperties`, `ClockConfig`. O domínio é português; a camada é inglês.
  As entidades não levam sufixo — o pacote `entity/` já diz o que são.
- **O Flyway é a única fonte do schema.** O Hibernate roda com `ddl-auto: validate`, então qualquer
  divergência entre entidade e tabela derruba a aplicação na subida — de propósito.
- **Nada de `Instant.now()` solto.** Tudo que precisa da hora atual recebe o bean `Clock` injetado,
  o que torna testáveis a virada de dia, o disparo de lembretes e o cálculo de `streak`.
- **Há banco em produção desde 03/10/2026: migração aplicada não se edita.** Toda mudança de schema
  vira uma migração nova (`V2`, `V3`, …). Editar uma já aplicada faz o Flyway recusar a subida em
  produção.

## Documentação

- [Documento de design](docs/superpowers/specs/2026-08-30-app-rotina-pessoal-design.md) — decisões
  de arquitetura, modelo de dados, telas, notificações e escopo do MVP
- [Planos de implementação](docs/superpowers/plans/) — um por fatia, com as decisões tomadas no
  caminho
