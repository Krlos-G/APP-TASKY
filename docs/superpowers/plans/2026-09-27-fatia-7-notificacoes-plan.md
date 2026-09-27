# Plano de Implementação — Fatia 7: Notificações

- **Data:** 2026-09-27
- **Projeto:** Tasky — https://github.com/Krlos-G/APP-TASKY
- **Spec de referência:** `docs/superpowers/specs/2026-08-30-app-rotina-pessoal-design.md` (seção 7)
- **Depende de:** Fatia 6 (concluída, PR #6 mergeado)
- **Status:** aguardando aprovação

---

## Objetivo da fatia

A outra metade do motivo do projeto. Até aqui o app só responde quando você abre; agora ele
**chama** na hora combinada.

### O critério de pronto do spec não cabe nesta fatia ⚠️

O spec diz *"recebo lembrete no iPhone no horário"*. **Não dá para provar isso aqui**: Web Push no
iOS exige o PWA instalado, instalar exige **HTTPS**, e HTTPS exige o deploy — que é a Fatia 8. Hoje
o backend só existe em `localhost`.

O que dá para fazer, e é o que proponho:

1. **Construir a coisa inteira** e provar ponta a ponta **no navegador do PC**, em `localhost`
   (onde service worker e Web Push funcionam sem HTTPS). Isso exercita chave VAPID, criptografia,
   inscrição, materialização, despacho e recebimento — tudo menos o iOS.
2. **O teste no iPhone vira critério de pronto da Fatia 8**, logo depois do deploy.

**Critério de pronto desta fatia:**

1. Ativo as notificações no PC e recebo o "enviar teste" na hora.
2. Marco um lembrete num hábito e recebo a notificação no horário, com o app fechado.
3. Concluo o item antes da hora e o lembrete **não** chega.
4. O servidor ficou fora do ar na hora → o lembrete atrasado é descartado, não entregue tarde.
5. `mvn verify` e os testes do front passam; CI verde no PR.

**Fora do escopo:** Telegram e lembrete "crítico" (v1.1 no spec), som de despertador, agrupamento
de notificações, e qualquer coisa de deploy.

---

## Decisões desta fatia

| Decisão | Escolha | Por quê |
|---|---|---|
| **Ordem das etapas** | **A lib primeiro, sozinha** | `nl.martijndwars:web-push` não tem release desde fev/2025 e nunca foi testada contra Java 21 / Boot 4. Se ela não funcionar, quero saber na primeira etapa, não na quinta. |
| **Verificação no PC, não no iPhone** ⚠️ | Ver acima | Falta HTTPS, que só vem no deploy. |
| Chave pública VAPID | **Endpoint**, não build-time | `GET /inscricoes-push/chave`. Trocar a chave não exige rebuildar o front. |
| Materialização | **Só o job de hora em hora**, sem gerar no `GET /dia` | O spec previa os dois. Gerar na leitura é escrita escondida num GET, e o ganho é de minutos numa coisa que já tolera ~60s de atraso. |
| O job **reconcilia**, não só cria | Cria o que falta, corrige o que mudou, cancela o que sobrou | Editar a hora de um hábito, arquivá-lo ou trocar de fuso deixaria lembretes velhos de pé. Uma reconciliação resolve os três casos sem espalhar invalidação por três serviços. |
| Service worker | **O do Angular (`ngsw`), sem escrever um** | O `ngsw` já exibe a notificação e trata o clique, desde que o payload venha no formato dele. Um SW próprio seria código de infraestrutura para manter à toa. |
| Lembrete atrasado | **Cancelado acima de 30 min** | Conforme o spec: um empurrão fora de hora é ruído, e pior que silêncio. |

A marcada com ⚠️ muda o critério de pronto do spec e precisa do seu ok.

---

## Etapa 1 — A lib de push, provada sozinha

Nada de domínio aqui. Só descobrir se a peça de risco funciona nesta stack.

- Dependência no `pom.xml` e geração do par de chaves VAPID (guardadas em
  `TASKY_VAPID_PUBLICA` / `TASKY_VAPID_PRIVADA` / `TASKY_VAPID_ASSUNTO`, nunca no repositório).
- Interface **`CanalNotificacao`** com uma implementação **`PushWeb`** — é a fronteira que permite
  trocar a lib sem mexer no resto.
- Teste que manda uma notificação para um **servidor de mentira** rodando no próprio teste, e
  confere o que saiu: cabeçalhos VAPID, `Content-Encoding: aes128gcm`, corpo cifrado não vazio.

**Ponto de decisão:** se a lib não funcionar em Java 21, eu paro aqui e a gente decide entre
implementar o protocolo na mão (é RFC 8291 + RFC 8292, cerca de 200 linhas com a BouncyCastle que a
própria lib já usa) ou procurar alternativa. **Não sigo para a Etapa 2 sem isso resolvido.**

---

## Etapa 2 — Inscrições push

| Método | Rota | Observação |
|---|---|---|
| GET | `/inscricoes-push/chave` | A pública VAPID, para o front pedir a inscrição |
| POST | `/inscricoes-push` | `endpoint`, `p256dh`, `auth`, `userAgent` |
| DELETE | `/inscricoes-push` | Desativar neste aparelho (pelo `endpoint`) |
| POST | `/inscricoes-push/testar` | Manda uma notificação agora, para todos os aparelhos |

Reinscrever o mesmo `endpoint` **atualiza** em vez de duplicar — o navegador renova a inscrição
sozinho de tempos em tempos, e o `endpoint` é único no banco.

**Verificação:** testes de controller, incluindo a reinscrição e o isolamento entre contas.

---

## Etapa 3 — Materialização dos lembretes

`LembreteService` + job `@Scheduled` de hora em hora, cobrindo as **próximas 48h**.

| Origem | Quando vira lembrete |
|---|---|
| **Bloco** | `minutosAntecedenciaLembrete != null` → dispara antes do início |
| **Hábito** | devido no dia e `horaLembrete != null` |
| **Tarefa** | `dataPlanejada` no período e `horaLembrete != null` |
| **Resumo** | `horaResumoDiario != null` no usuário (a coluna já existe) |

- `disparar_em = ZonedDateTime.of(data, hora, fuso do usuário).toInstant()`.
- Idempotente pelo `UNIQUE (usuario, tipo_origem, origem_id, data_ref)` que a `V1` já tem.
- **Reconciliação:** o que mudou de horário é corrigido; o que perdeu a regra (hábito arquivado,
  hora apagada, tarefa concluída) vira `CANCELADO` enquanto ainda está `PENDENTE`.

**Verificação:** testes com relógio fixo — cada uma das quatro origens, a idempotência (rodar duas
vezes não duplica), a correção de horário e o cancelamento do órfão.

---

## Etapa 4 — Despacho e entrega

Job `@Scheduled(fixedDelay = 60s)`: `status = PENDENTE AND disparar_em <= agora`.

Antes de enviar, **revalida**:

- Hábito ou tarefa já concluído → `CANCELADO`.
- Atraso maior que 30 min → `CANCELADO`.
- Sem inscrição nenhuma → `CANCELADO` (não adianta tentar).

Envia para todas as inscrições do usuário. Resposta `404`/`410` do serviço de push → **apaga a
inscrição** (aparelho que desinstalou o app). Sucesso → `ENVIADO`. Falha transitória → segue
`PENDENTE` e soma tentativa; na terceira, `FALHOU`.

Payload no formato do `ngsw`, com `tag` por origem para não empilhar duplicado e a URL de destino
no clique.

**Verificação:** testes com um `CanalNotificacao` de mentira — envio feliz, cancelamento por
conclusão, cancelamento por atraso, remoção da inscrição no 410, e a contagem de tentativas.

---

## Etapa 5 — `Ajustes` e os campos de lembrete

A tela nova, em `/ajustes`, alcançada pelo `Resumo`:

- Estado da permissão e o botão **Ativar notificações**.
- **Instrução para iOS** quando o app não está instalado na tela de início — sem isso, o botão
  simplesmente não funciona no iPhone e ninguém entende por quê.
- **Enviar teste**.
- **Resumo diário**: horário ou desligado.
- O **fuso em uso**, só leitura (ele vem do aparelho desde a Fatia 6).
- Sair da conta, que hoje mora no canto da navegação.

E os campos que ficaram esperando esta fatia:

- `horaLembrete` no formulário de **hábito** e no de **tarefa**.
- `minutosAntecedenciaLembrete` no formulário de **bloco**.

**Verificação:** testes de componente e verificação no navegador.

---

## Etapa 6 — Fechamento

- **Teste de fluxo** (`FluxoLembretesTests`): hábito com lembrete → job materializa → relógio
  avança → despachante envia → segundo despacho não reenvia. E a variante: concluo antes da hora e
  o lembrete sai como `CANCELADO`.
- **Verificação no navegador do PC**, com notificação de verdade aparecendo na tela.
- PR com CI verde.

---

## Ordem e pontos de parada

```
1. Prova da lib ─────────── PONTO DE DECISAO: a lib funciona em Java 21?
2. Inscricoes push ──────── consigo registrar um aparelho e receber um teste
3. Materializacao ───────── os lembretes das proximas 48h aparecem na tabela
4. Despacho ─────────────── o lembrete sai no horario, e o atrasado nao sai
5. Ajustes e campos ─────── ponto de parada: ativo e configuro pela interface
6. Fechamento ───────────── FATIA 7 CONCLUIDA (no PC; iPhone fica para a 8)
```

**Branch:** `feature/etapa7-notificacoes`.

---

## Riscos

| Risco | Contenção |
|---|---|
| **A lib `web-push` não funcionar em Java 21 / Boot 4** | É a Etapa 1 inteira, antes de qualquer coisa depender dela. Plano B combinado antes de seguir. |
| Chave VAPID vazar no repositório | Env var desde o primeiro commit, como o segredo do JWT. `.env.example` ganha as duas com valor de exemplo. |
| Lembrete duplicado no mesmo dia | O `UNIQUE NULLS NOT DISTINCT` da `V1` já existe exatamente para isso, inclusive para o resumo, que tem `origem_id` nulo. |
| Lembrete velho disparando depois de eu mudar a rotina | A reconciliação corrige e cancela; há teste para os dois. |
| O iOS se comportar diferente do PC | Fica explícito como pendência da Fatia 8. É a razão de o critério de pronto mudar. |

---

## Próxima fatia

**A fatia de design** — combinado: entre a 7 e a 8. Vou te pedir exemplos de apps de iPhone que
você acha bonitos antes de propor qualquer direção.
