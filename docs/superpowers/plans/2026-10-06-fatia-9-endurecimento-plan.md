# Plano de Implementação — Fatia 9: Pronto para duas pessoas

- **Data:** 2026-10-06
- **Projeto:** Tasky — https://github.com/Krlos-G/APP-TASKY
- **Depende de:** Fatia 8 e as abas deslizantes (mergeadas e testadas no iPhone)
- **Status:** aguardando aprovação

> Não confundir com `2026-10-03-fatia-9-app-iphone-plan.md`, o plano B do app nativo, arquivado.

---

## Objetivo da fatia

O spec chamava esta fatia de "Exportar + endurecimento". Com a irmã do Carlos usando o app desde
06/10, a régua mudou: perder dados, o servidor cair sem ninguém saber ou alguém ficar preso numa
senha que não escolheu agora afeta outra pessoa.

**O exportar saiu** (decidido em 06/10): ele era uma rede de segurança manual de antes de existir
backup. Com o backup automático, vira código a manter para um uso que não deve acontecer — e o
Carlos, dono do servidor, tira qualquer dado de um backup se precisar.

**Critério de pronto:**

1. Todo dia sai um backup do banco para fora do Railway, e uma restauração foi testada de verdade.
2. Se o servidor cair — ou se os lembretes pararem de sair —, chega um aviso.
3. Cada um troca a própria senha nos Ajustes.
4. As pendências pequenas estão resolvidas.
5. `mvn verify` e os testes do front passam; CI verde.

**Fora do escopo:** exportar, "esqueci a senha" por e-mail, limitar as concluídas, despacho a cada
15 s, Rotina arrastável, estados vazios ilustrados, ver o dia sem internet.

---

## Decisões desta fatia

| Decisão | Escolha | Por quê |
|---|---|---|
| Onde roda o backup | **GitHub Actions, toda madrugada**: `pg_dump` do banco do Railway | Grátis, fora do Railway (sobrevive se o projeto lá sumir) e sem código no app. |
| Onde fica o backup | **Artefato do próprio workflow, guardado por 30 dias**, no repositório privado | O banco tem poucos KB; cabe folgado na cota grátis. Só quem acessa o repositório baixa. |
| Como o GitHub alcança o banco ⚠️ | **Rede pública do Postgres no Railway ligada de vez** (TCP proxy), com a URL num segredo do GitHub | O banco fica acessível pela internet, protegido pela senha que o Railway gera (32 caracteres) e por TLS. A alternativa sem expor o banco — um serviço agendado dentro do Railway — guardaria o backup no mesmo lugar que pode sumir. |
| Backup testado | **Uma restauração de verdade no banco local**, documentada no README | Backup que nunca foi restaurado é só esperança. |
| Aviso de queda | **UptimeRobot (plano grátis)** consultando `/actuator/health` a cada 5 min, com e-mail | Configuração, sem código. |
| Lembretes parados ⚠️ | **O health check passa a dizer DOWN se o despacho de lembretes não roda há mais de 5 min** | O servidor pode estar no ar com o agendador travado — e aí os lembretes param em silêncio. Uma classe pequena, com teste. |
| Onde mora o "trocar senha" | **`PUT /api/v1/auth/senha`**, com a senha atual e a nova | O cookie de sessão só vai para `/api/v1/auth`: ali o servidor sabe qual é a sessão de quem está trocando. |
| Sessões depois da troca | **Encerra as outras; a deste aparelho continua** | Se a senha vazou, quem estiver usando-a em outro aparelho sai na hora. O preço: trocar no iPhone desloga o PC. |
| Lembrete cancelado | **Volta a valer se a regra voltar e o horário ainda não passou** | Concluir uma tarefa por engano e desfazer deixava o lembrete cancelado. Os que passaram da hora continuam cancelados — o despacho os descartaria de novo. |

As marcadas com ⚠️ precisam do seu ok.

---

## O que é seu, e não meu

- **Railway:** ligar a rede pública do Postgres e copiar a `DATABASE_PUBLIC_URL`.
- **GitHub:** criar o segredo `BANCO_PRODUCAO_URL` com essa URL.
- **UptimeRobot:** criar a conta e o monitor (eu passo o passo a passo).
- **Até ~02/11:** decidir o Railway — com duas pessoas usando, a recomendação é pagar o Hobby.

---

## Etapa 1 — Backup automático

- Workflow `backup.yml`: toda madrugada (e com um botão para rodar na hora), `pg_dump` em formato
  custom com o Postgres 18, artefato com a data no nome, guardado por 30 dias.
- Se o backup falhar, o GitHub manda e-mail (é o padrão para workflow agendado que falha).
- **Restauração testada:** baixar um artefato e restaurar no banco local do compose; conferir que
  as tarefas e os hábitos estão lá. O passo a passo vai para o README.

## Etapa 2 — Aviso se cair

- `HealthIndicator` dos lembretes: o despacho anota quando rodou pela última vez; mais de 5 min
  sem rodar vira DOWN. Testes com o relógio controlado.
- UptimeRobot no `/actuator/health`, com e-mail.
- Prova: derrubar o app local (ou simular o despacho parado) e ver o health ficar DOWN.

## Etapa 3 — Trocar a senha

- **Backend:** `PUT /api/v1/auth/senha` com `senhaAtual` e `novaSenha` (12+ caracteres, como no
  cadastro). Senha atual errada → erro claro; a nova não pode ser igual à atual. Grava o hash novo
  e encerra as outras sessões. Testes: troca; senha atual errada; nova curta; as outras sessões
  caem e esta continua.
- **Front:** nos Ajustes, em Conta, "Trocar senha" abre um formulário de campo-cartão (atual, nova,
  confirmar a nova), com as mensagens de erro de sempre. Testes.

## Etapa 4 — Pendências pequenas

- **Lembrete que volta:** na geração de lembretes, um cancelado volta a pendente se a regra voltou
  e o horário ainda não passou. Teste: concluir e desfazer uma tarefa com lembrete para daqui a
  pouco faz o lembrete valer de novo.
- **Ícone nas notificações do PC:** o payload do Web Push passa a levar o ícone do Tasky.
- **Aviso de senha no log:** desligar a configuração automática do Spring que cria um usuário de
  memória — o app não usa login por formulário, e a senha gerada só aparecia no log.

## Etapa 5 — Fechamento

README (backup e restauração, monitoramento, trocar senha), memória, PR. Depois dela, o v1 do
Tasky está completo.

---

## Riscos

| Risco | Como trato |
|---|---|
| Banco exposto na internet para o backup | Senha gerada pelo Railway e TLS. Se incomodar, dá para ligar a rede pública só na hora do backup — mas aí ele deixa de ser automático. |
| Health check DOWN por engano (ex.: logo depois de subir) | O limite conta a partir da subida: os primeiros 5 min não são falha. |
| Trocar a senha desloga os outros aparelhos | É o comportamento escolhido; fica dito na tela, embaixo do botão. |
