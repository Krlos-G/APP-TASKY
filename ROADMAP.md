# Roadmap

O que já está pronto, o que está em andamento e o que vem depois. Cada item grande vira um plano
em [`docs/superpowers/plans/`](docs/superpowers/plans/) antes de começar.

## Pronto (v1, set–out/2026)

- Fundação: Spring Boot + Angular PWA, PostgreSQL com Flyway, CI no GitHub Actions
- Autenticação: JWT com refresh token rotativo em cookie, rate limit de login, cadastro por convite
- Rotina: modelos de dia, blocos com horário e cor, semana montada por modelo
- Hoje: linha do tempo do dia, bloco atual, hábitos, tarefas e atrasadas
- Hábitos com streak, tarefas com lista única por data, Resumo do dia e da semana
- Notificações por Web Push (VAPID), com lembretes gerados ao salvar
- Design próprio (vidro fosco, abas deslizantes) e PWA instalável no iPhone
- Deploy em contêiner único no Railway

## Em andamento

### Migração para a Oracle Cloud

O período grátis do Railway acabou e o app está fora do ar. Roteiro completo em
[`2026-11-09-migracao-oracle-roteiro.md`](docs/superpowers/plans/2026-11-09-migracao-oracle-roteiro.md).

- [ ] Máquina Always Free (Ampere A1, Ubuntu 24.04) com Docker, swap e firewall nas duas camadas
- [ ] `docker-compose.prod.yml` com PostgreSQL, app e Caddy (HTTPS automático pelo Let's Encrypt)
- [ ] Subdomínio no DuckDNS
- [ ] Segredos novos (JWT e par VAPID) só no `.env` da máquina
- [ ] Contas recriadas, cadastro fechado de novo e notificações testadas no iPhone
- [ ] Seção "Produção" do README reescrita para a Oracle

## Próximo: endurecimento (Fatia 9)

Plano em [`2026-10-06-fatia-9-endurecimento-plan.md`](docs/superpowers/plans/2026-10-06-fatia-9-endurecimento-plan.md),
a ser revisto para a Oracle depois da migração.

- [ ] Backup automático diário do banco, guardado fora do servidor, com uma restauração testada
- [ ] Health check que fica DOWN se o despacho de lembretes parar, mais monitor externo com aviso
- [ ] Trocar a senha nos Ajustes (`PUT /api/v1/auth/senha`), encerrando as outras sessões
- [ ] Lembrete cancelado volta a valer quando a tarefa é desmarcada antes do horário
- [ ] Ícone do Tasky nas notificações do PC
- [ ] Desligar o usuário em memória que o Spring cria por padrão (senha gerada aparecendo no log)

## Depois (v1.1)

- [ ] Marcar blocos da rotina como feitos, por data
- [ ] Histórico de hábitos em grade, no estilo do gráfico de contribuições
- [ ] Resumo com alternância entre semana e mês
- [ ] Rotina reorganizável arrastando os blocos
- [ ] Estados vazios ilustrados

## Ideias para mais adiante

- Ajustar um dia específico sem mexer no modelo da semana
- Meta numérica em hábito (ex.: "2 litros de água")
- Histórico da agenda do hábito, para o streak não mudar quando a agenda muda
- Ver o dia sem internet
- "Esqueci a senha" por e-mail
- Revisão no fim do dia

## Descartado

- **Exportar dados em JSON** (06/10/2026): com backup automático, vira código a manter para um uso
  que não deve acontecer.
- **App nativo com Capacitor e TestFlight** (out/2026): o Tasky segue como PWA instalado na tela
  de início; o plano fica arquivado em `2026-10-03-fatia-9-app-iphone-plan.md`.
