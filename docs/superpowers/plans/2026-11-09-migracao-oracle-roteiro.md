# Roteiro de migração — Tasky para a Oracle Cloud

- **Data:** 2026-11-09
- **Projeto:** Tasky — https://github.com/Krlos-G/APP-TASKY
- **Para quem:** agentes de IA executando a migração, sob supervisão do Carlos
- **Status:** pronto para execução

---

## 1. Contexto

O Tasky é um app pessoal de rotina, hábitos e tarefas, usado por duas pessoas como app instalado
no iPhone (PWA) e no navegador do PC. Ele roda num contêiner só: o Spring Boot serve o front em
Angular e a API no mesmo endereço, e conversa com um PostgreSQL 18.

Ele rodava no Railway, cujo teste grátis acabou: **hoje o app está fora do ar.** A migração vai
para uma máquina **Always Free da Oracle Cloud**, e **começa com o banco vazio** — os dados antigos
não serão recuperados (decisão do Carlos).

## 2. Objetivo

O Tasky no ar em HTTPS, num subdomínio grátis do DuckDNS, rodando na máquina da Oracle, ligado
24 horas por dia — é o servidor que dispara os lembretes no horário.

## 3. Estado de partida (feito pelo Carlos antes)

- [ ] Conta na Oracle Cloud com a região de origem **São Paulo** (é permanente).
- [ ] Máquina criada: **Ampere A1 (ARM), 1 OCPU e 2 GB de memória**, Ubuntu 24.04, com IP público.
  2 GB e não mais, de propósito: a Oracle recupera máquinas em que CPU, rede **e memória** ficam
  abaixo de 20% por 7 dias. Com 2 GB, o Tasky sozinho passa de 20% da memória. Se faltar
  capacidade de A1, a alternativa é a `VM.Standard.E2.1.Micro` (AMD, 1 GB) — aí a swap da etapa 1
  é obrigatória.
- [ ] Subdomínio no DuckDNS (ex.: `tasky-carlos.duckdns.org`) apontando para o IP da máquina.
- [ ] Chave SSH própria dos agentes autorizada na máquina, para o usuário `ubuntu`.

**Os agentes não recebem o login da conta Oracle nem o token do DuckDNS.** Tudo o que pede o
console da Oracle volta para o Carlos.

## 4. Regras

1. **Segredos nascem na máquina e ficam nela**, num `.env` com permissão `600`. Nunca no chat,
   no repositório, em log ou em mensagem de commit.
2. **Pedir aprovação do Carlos antes de:** abrir portas no firewall, apagar disco ou volume,
   reiniciar a máquina, mexer no banco depois que alguém já tiver criado conta.
3. **Não fazer commit nem push** — o Carlos controla o Git. Arquivos novos para o repositório são
   entregues prontos, com a mensagem de commit sugerida.
4. Não alterar código do app. Se algo do app impedir a migração, parar e relatar.
5. Só as portas **22, 80 e 443** abertas para a internet. O Postgres e a porta 8080 nunca.

## 5. Etapas

### Etapa 1 — Preparar a máquina

- Atualizar o sistema; ativar as atualizações de segurança automáticas (`unattended-upgrades`).
- Instalar Docker Engine e o plugin compose (repositório oficial do Docker).
- Criar **2 GB de swap** — o build da imagem compila o Angular e o Maven, e pode passar da memória.
- **Firewall, nas duas camadas** (armadilha conhecida da Oracle): a regra de entrada para 80 e 443
  na *Security List* da VCN é do Carlos, no console; os agentes abrem as mesmas portas no
  `iptables` da imagem Ubuntu da Oracle, que vem bloqueando tudo menos a 22, e tornam a regra
  persistente.

### Etapa 2 — Subir o Tasky

- Clonar o repositório na máquina e construir a imagem do `Dockerfile` da raiz **na própria
  máquina** — ela é ARM, e as imagens base (Node 24, Maven, Temurin 21) têm versão arm64.
- Criar um compose de produção (entregue como `docker-compose.prod.yml` para o repositório) com:
  - **`db`:** `postgres:18-alpine`, volume nomeado, sem porta publicada, `restart: unless-stopped`;
  - **`app`:** a imagem do Tasky, sem porta publicada para a internet, `restart: unless-stopped`,
    healthcheck em `/actuator/health`;
  - **`caddy`:** `caddy:2`, portas 80 e 443, volume para os certificados, proxy para `app:8080`.
    O Caddy tira o certificado do Let's Encrypt sozinho.
- Variáveis do app, no `.env` da máquina (ver a seção "Produção" do README):
  - `SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/tasky`, usuário e **senha nova e aleatória**;
  - `TASKY_JWT_SECRET` e o par `TASKY_VAPID_PUBLICA`/`TASKY_VAPID_PRIVADA` **novos** (o comando
    de geração está no README);
  - `TASKY_VAPID_ASSUNTO=https://<subdominio>.duckdns.org`;
  - `TASKY_COOKIE_SECURE=true`, `TASKY_COOKIE_SAMESITE=Lax`;
  - `SPRINGDOC_API_DOCS_ENABLED=false`, `SPRINGDOC_SWAGGER_UI_ENABLED=false`;
  - `TASKY_CODIGO_CONVITE`: **só durante a criação das contas** (etapa 4), depois removido.
- O limite de memória do Java já vem na imagem (`-Xmx256m`). Não aumentar.

### Etapa 3 — Provar que está no ar

De fora da máquina:
- `https://<subdominio>.duckdns.org/actuator/health` responde `{"status":"UP"...}`;
- `/` e `/hoje` devolvem o app (HTML com `<app-root>`); `/api/v1/dia` sem login devolve **401**;
- o certificado é válido (Let's Encrypt), e `http://` redireciona para `https://`;
- de fora, as portas 5432 e 8080 **não** respondem;
- o log do app não tem erro na subida, e mostra o Flyway aplicando as migrações num banco vazio.

### Etapa 4 — Contas e iPhone (com o Carlos)

- O Carlos define o código de convite no `.env`, os agentes reiniciam o app, e cada um cria a conta
  pelo comando de cadastro do README (trocando o endereço). Depois, o código sai do `.env` e o app
  reinicia — **o cadastro tem de voltar a recusar** (403).
- No iPhone: limpar os dados do site antigo, instalar o app novo pelo Safari com **Abrir como App
  Web** ligado, entrar, ativar as notificações, "Enviar teste".

### Etapa 5 — Entrega

- Para o repositório: `docker-compose.prod.yml`, `Caddyfile` e a seção "Produção" do README
  reescrita para a Oracle (subir, atualizar o app, ver logs, criar conta, backup manual), com a
  mensagem de commit sugerida.
- Um relatório curto: o que foi feito, o que deu errado no caminho, e o uso de memória em repouso
  (`docker stats`).

## 6. Critério de pronto

1. O app abre em `https://<subdominio>.duckdns.org`, com certificado válido.
2. As duas contas criadas, e o cadastro fechado de novo.
3. O "Enviar teste" chega no iPhone, e um lembrete criado para daqui a 5 minutos chega com o app
   fechado.
4. Depois de reiniciar a máquina, tudo volta sozinho, sem ninguém entrar nela.
5. Nenhum segredo fora do `.env` da máquina.

## 7. Fora deste roteiro

Backup automático, monitoramento e o resto da Fatia 9 — o plano dela será revisto para a Oracle
depois da migração (o backup previsto contava com o banco do Railway).
