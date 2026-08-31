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

Com o backend no ar, a documentação da API fica em <http://localhost:8080/swagger-ui.html> e o
health check em <http://localhost:8080/actuator/health>.

## Estrutura

```
.
├─ backend/                     Spring Boot
│  └─ src/main/
│     ├─ java/br/com/tasky/
│     │  ├─ config/             beans de infraestrutura (Clock)
│     │  ├─ dominio/            entidades JPA, enums e conversores
│     │  └─ repositorio/        repositórios Spring Data
│     └─ resources/
│        ├─ application.yml
│        └─ db/migration/       migrações Flyway
├─ frontend/                    Angular PWA
│  └─ src/app/
│     ├─ paginas/               uma pasta por tela
│     └─ app.routes.ts
├─ docs/superpowers/
│  ├─ specs/                    documento de design
│  └─ plans/                    planos de implementação por fatia
└─ docker-compose.yml           PostgreSQL de desenvolvimento
```

## Convenções

- **Nomes de domínio em português**: tabelas e colunas em `snake_case` (`bloco_modelo`,
  `hora_inicio`), classes em `PascalCase` (`BlocoModelo`), enums em maiúsculas (`A_FAZER`).
  Exceção deliberada: `streak` fica em inglês.
- **O Flyway é a única fonte do schema.** O Hibernate roda com `ddl-auto: validate`, então qualquer
  divergência entre entidade e tabela derruba a aplicação na subida — de propósito.
- **Nada de `Instant.now()` solto.** Tudo que precisa da hora atual recebe o bean `Clock` injetado,
  o que torna testáveis a virada de dia, o disparo de lembretes e o cálculo de `streak`.
- Enquanto não houver banco em produção, a migração `V1` pode ser editada livremente (basta
  recriar o volume). Depois do primeiro deploy, toda mudança vira `V2`, `V3`, …

## Documentação

- [Documento de design](docs/superpowers/specs/2026-08-30-app-rotina-pessoal-design.md) — decisões
  de arquitetura, modelo de dados, telas, notificações e escopo do MVP
- [Plano da Fatia 1](docs/superpowers/plans/2026-08-31-fatia-1-fundacao-plan.md) — fundação
