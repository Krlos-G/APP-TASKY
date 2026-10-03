# Plano de Implementação — Fatia 9: App de iPhone

- **Data:** 2026-10-03
- **Projeto:** Tasky — https://github.com/Krlos-G/APP-TASKY
- **Depende de:** Fatia 8 (backend no ar no Railway; falta fechar o PR)
- **Status:** aprovado em 03/10 (com as três decisões marcadas)

---

## Por que esta fatia existe

Na Fatia 8 o Tasky foi ao ar e o Carlos o instalou pela tela de início do iPhone. O veredito dele:
**"tem que ser instalado de verdade, não adianta abrir como página web."** Em 03/10 ficou decidido:

- **App nativo** — o plano B guardado desde o início do projeto.
- **Capacitor** embrulhando o Angular que já existe: telas, vidro, gestos e ícone continuam os
  mesmos. Reescrever em outra tecnologia seria jogar fora o front inteiro.
- **Codemagic** compila no Mac da nuvem, porque aqui não há Mac.
- **Conta Apple Developer paga (US$ 99/ano)** e instalação pelo **TestFlight**.

O backend, o Railway e a versão web (que segue sendo a do PC) continuam como estão.

**Critério de pronto:**

1. O Tasky está instalado pelo TestFlight, com o ícone, e abre como app — sem nada do Safari.
2. O login persiste entre aberturas; o que marco no iPhone aparece no PC, e o contrário.
3. Ativo as notificações no app e o "enviar teste" chega.
4. Um lembrete chega no horário com o app fechado — **inclusive um criado para daqui a 10 min**.
5. Um deploy do front no Railway aparece no app sem precisar de build novo.
6. `mvn verify` e os testes do front passam; CI verde.

**Fora do escopo:** publicar na App Store, Android, widget, Live Activity, funcionar sem
internet, lembrete agendado no próprio aparelho (só se o push da Apple decepcionar), tela de
cadastro.

---

## Decisões desta fatia

| Decisão | Escolha | Por quê |
|---|---|---|
| **O front dentro do app** ⚠️ | **O app abre o Tasky do Railway** (`server.url` do Capacitor) | O app e a API continuam no mesmo endereço, então o cookie de login segue funcionando como hoje. Empacotar o front dentro do app mudaria a origem para `capacitor://localhost`: o cookie de renovação viraria "de terceiro", e o WebView do iOS bloqueia isso — seria refazer a autenticação com HTTP nativo e CORS. De brinde, **um deploy no Railway atualiza o app sem build novo**. Contra: a documentação do Capacitor diz que isso "não é indicado para produção". Os motivos são a revisão da App Store e o uso sem internet, e nenhum dos dois vale para um app pessoal no TestFlight. Se um dia for para a App Store, empacotar o front vira uma fatia. |
| **Notificações** | **Push nativo da Apple (APNs), enviado pelo servidor** | A Fatia 7 já decide *quando* avisar (gerar e despachar lembretes); muda só o *canal* de entrega. O APNs é o que todo app nativo usa. O lembrete agendado no próprio aparelho fica de reserva, se o APNs atrasar. |
| **Lembretes em cima da hora** ⚠️ | **Gerar os lembretes do usuário ao salvar** | Bug achado na Fatia 8: os lembretes só são gerados aos 5 min de cada hora, então um lembrete criado para antes da próxima rodada chega até 30 min atrasado — ou não chega. Sem isto, o critério 4 não passa, e a medição de pontualidade mediria um atraso nosso. |
| Cliente do APNs | **`HttpClient` do Java (HTTP/2) + jjwt (ES256)**, sem biblioteca nova | Os dois já estão no projeto. O protocolo é um POST com um token assinado. |
| Inscrições | **`inscricao_push` ganha a coluna `tipo` (WEB/APNS)** | O `Notificador` escolhe o canal pelo tipo. O Web Push continua valendo para o PC. |
| Build | **Codemagic, disparado à mão**, com `codemagic.yaml` no repositório | O plano grátis dá 500 min/mês em Mac M2, umas 30 compilações. Com o front vindo do Railway, só se compila quando muda algo nativo. |
| Nome e identificador | Identificador `br.com.tasky.app`; na tela de início, "Tasky" | O App Store Connect exige nome **único**, e "Tasky" deve estar tomado: o registro lá pode chamar "Tasky Rotina" sem mudar o nome no iPhone. |
| Numeração ⚠️ | Esta vira a **Fatia 9**; "Exportar + endurecimento" vira a **10** | |
| Fatia 8 | **A Etapa 5 (instalar o PWA no iPhone) sai**; o fechamento (README e PR) vira a Etapa 0 daqui | |

As marcadas com ⚠️ precisam do seu ok.

---

## O que é seu, e não meu

Contas, pagamentos e chaves secretas ficam com você. Para cada um eu preparo o passo a passo:

- **Inscrição no Apple Developer Program** — a Apple confere identidade, costuma levar 1–2 dias.
- **Registro do app no App Store Connect** (nome, identificador, SKU).
- **Chave da API do App Store Connect** (papel *App Manager*), cadastrada no Codemagic.
- **Chave do APNs** (arquivo `.p8`), colada nas variáveis do Railway.
- **Conta no Codemagic** (entrando com o GitHub).
- **App TestFlight** no iPhone, com você como testador interno.

---

## Etapa 0 — Fechar a Fatia 8

- README: como fazer deploy, as variáveis de produção, o comando de backup manual e as
  configurações do Railway feitas no painel.
- Você abre o PR da Fatia 8; depois do merge, o Railway passa a publicar da `main`, com
  "Wait for CI".

## Etapa 1 — Lembretes gerados ao salvar

Só backend; não depende da Apple — dá para fazer enquanto a conta é aprovada.

- Salvar ou excluir tarefa, hábito, bloco da rotina ou o horário do resumo dispara um evento
  ("a agenda mudou"). Um ouvinte, **depois do commit**, gera os lembretes daquele usuário. A
  geração já reconcilia — cria o que falta, corrige o que mudou, cancela o que sobrou —, então
  chamá-la a cada salvamento é seguro.
- Testes: tarefa com lembrete para daqui a 10 min tem o lembrete gerado na hora; mudar o horário
  corrige; excluir cancela; erro na geração não desfaz o salvamento.

## Etapa 2 — O projeto iOS

- `@capacitor/core`, `@capacitor/cli` e `@capacitor/ios` no front; `capacitor.config.ts` com o
  identificador, o nome e o endereço do Railway.
- `npx cap add ios` com Swift Package Manager (padrão no Capacitor 8, sem CocoaPods). Gerar
  funciona no Windows; só compilar exige Mac. A pasta `ios/` vai para o repositório.
- Ícone do app em 1024 px, saído do mesmo SVG (`frontend/icones/gerar.sh` ganha mais uma linha).
- `Info.plist`: declarar que o app só usa a criptografia padrão (HTTPS) — sem isso, o TestFlight
  pergunta a cada build.

## Etapa 3 — Codemagic e o primeiro TestFlight

- `codemagic.yaml`: instala, compila o front, sincroniza o Capacitor, busca os certificados pela
  chave da API, numera o build a partir do último do TestFlight, gera o `.ipa` e envia para o
  TestFlight.
- Você: conta no Codemagic, a chave da API cadastrada, o registro do app no App Store Connect.
- **Ponto de parada:** o Tasky no iPhone como app. Conferimos login, gestos, área segura, cor da
  barra de status e se a rolagem com blur engasga.
- **Esta etapa também prova o risco maior da fatia:** se os plugins nativos funcionam com a página
  vindo do Railway. Se não funcionarem, paramos antes de construir o push em cima disso.

## Etapa 4 — Push nativo

- **Backend:** migração V2 (`tipo` na `inscricao_push`; as chaves do Web Push viram opcionais),
  canal APNs (token assinado com a chave `.p8`, renovado a cada 50 min; resposta 410 = aparelho
  desinstalou, apaga a inscrição), `Notificador` escolhendo o canal pelo tipo, endpoint para o app
  registrar o token do aparelho.
- **Testes do canal sem rede de verdade:** nesta máquina o Java não abre conexão local (o
  antivírus corporativo bloqueia), então o envio fica atrás de uma interface, e os testes usam um
  transporte falso. O envio real se prova no iPhone.
- **Front:** dentro do app, os Ajustes pedem a permissão pelo plugin nativo
  (`@capacitor/push-notifications`) e mandam o token ao servidor. No navegador do PC, segue o Web
  Push de hoje.
- **Projeto iOS:** a permissão de push (`aps-environment`) no arquivo de entitlements.
- **Você:** cria a chave do APNs e cola nas variáveis do Railway.
- **Prova:** o "enviar teste" chega; um lembrete chega com o app fechado; concluir antes cancela.

## Etapa 5 — Pontualidade e fechamento

- Uns dias de uso: você anota quando os lembretes chegam, eu comparo com os logs do Railway.
- README (build do iOS, chaves, renovar o TestFlight a cada 90 dias), memória, commits.

---

## Ordem e pontos de parada

```
0. Fechar a Fatia 8                ← enquanto a Apple aprova a conta
1. Lembretes gerados ao salvar     ← idem
2. Projeto iOS                     ← idem
3. Codemagic + TestFlight          ← exige a conta aprovada; parada: o app no iPhone
4. Push nativo
5. Pontualidade e fechamento
```

---

## Riscos

| Risco | Tamanho | Como trato |
|---|---|---|
| **Plugins nativos não funcionarem com a página vindo do Railway** | **Médio** | A Etapa 3 prova antes da 4. Plano B: empacotar o front e mover a autenticação para HTTP nativo. |
| Ajuste que no Mac se faz no Xcode, e aqui é editando arquivo do projeto à mão (entitlements, assinatura) | Médio | Os logs do Codemagic dizem o que falta; o que der, vai para scripts do `codemagic.yaml`. |
| Aprovação da conta Apple demorar | Baixo | As etapas 0–2 não dependem dela. |
| 500 min do Codemagic acabarem | Baixo | O front vem do Railway: só se compila quando muda algo nativo. |
| Build do TestFlight expira em 90 dias | Baixo | Um clique no Codemagic a cada 3 meses; fica no README. |
| **O teste grátis do Railway acaba em ~02/11** | Decisão | Até lá: pagar o Hobby (~US$ 5/mês) ou migrar. A decisão é sua, com os números de uso. |

**Custo total:** US$ 99/ano da Apple, mais o Railway depois do teste (~US$ 5/mês). Codemagic
grátis.

---

## Próxima fatia

**Fatia 10 — Exportar + endurecimento.** Com o que a Fatia 8 empurrou para lá: backup automático
do banco, monitoramento de uptime, ícone nas notificações do PC e o aviso de senha gerada no log
de subida. O cache offline do dia perde sentido com o front vindo do Railway — reavaliar lá.
