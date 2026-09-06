# Plano de Implementação — Fatia 2: Autenticação

- **Data:** 2026-08-31
- **Projeto:** Tasky — https://github.com/Krlos-G/APP-TASKY
- **Spec de referência:** `docs/superpowers/specs/2026-08-30-app-rotina-pessoal-design.md` (seção 8)
- **Depende de:** Fatia 1 (concluída, CI verde)
- **Status:** aguardando execução

---

## Objetivo da fatia

Fechar a aplicação: ninguém entra sem login, e a sessão se mantém entre reloads e entre os dois
dispositivos sem pedir senha o tempo todo.

**Critério de pronto (Definition of Done):**

1. Consigo me registrar uma vez, usando o código de convite.
2. Consigo logar e ver as quatro telas; sem login, sou levado para `/login`.
3. Recarregar a página **não** me desloga.
4. O access token expira em ~20 min e é renovado sozinho, sem eu perceber.
5. Logout revoga a sessão no servidor, não só no navegador.
6. `mvn verify` e os testes do front passam, cobrindo rotação, replay e a janela de graça.

**Fora do escopo:** "esqueci a senha" (precisa de e-mail), tela de Ajustes, cadastro de mais de um
usuário pela interface.

---

## Decisões desta fatia

| Decisão | Escolha | Por quê |
|---|---|---|
| Biblioteca JWT | **jjwt 0.13.0** | Escolhida pelo autor; API direta. Dispensa o starter `oauth2-resource-server` — bastam `spring-boot-starter-security` e um filtro próprio. A versão não é gerenciada pelo Boot, então fica fixada numa property. |
| Origem front/API | **Configurável** | A política do cookie vem de variável de ambiente e funciona tanto em origem única quanto separada. A decisão real fica para a Fatia 8. |
| Hash da senha | **BCrypt** | Padrão do Spring Security, com salt embutido. |
| Hash do refresh token | **SHA-256** | O token já é aleatório de 256 bits, então não há o que "adivinhar": BCrypt aqui só adicionaria latência a cada refresh, sem ganho real. O caso é diferente do da senha, que tem baixa entropia. |
| Rate limit | **Em memória** | Instância única no MVP. Sem dependência nova. |

---

## Etapa 1 — Dependências e configuração

**Dependências novas no `pom.xml`:**

| Dependência | Escopo |
|---|---|
| `spring-boot-starter-security` | compile |
| `io.jsonwebtoken:jjwt-api` (0.13.0) | compile |
| `io.jsonwebtoken:jjwt-impl` | runtime |
| `io.jsonwebtoken:jjwt-jackson` | runtime |
| `spring-boot-starter-security-test` | test |

> No Boot 4 os starters de segurança mudaram de nome em relação ao Boot 3 (por exemplo,
> `spring-boot-starter-security-oauth2-resource-server`). Confirmar os nomes contra o Initializr,
> nunca de memória — foi assim que a Fatia 1 evitou um `pom` errado.

**Novas variáveis de ambiente** (documentadas no `.env.example`):

| Variável | Para quê |
|---|---|
| `TASKY_JWT_SECRET` | Chave HMAC do access token. **Mínimo 32 bytes**; a aplicação recusa subir se for menor. |
| `TASKY_CODIGO_CONVITE` | Código exigido no registro. **Vazio ou ausente desabilita o registro** — default seguro. |
| `TASKY_COOKIE_SAMESITE` | `Lax` (origem única) ou `None` (origens separadas). Default `Lax`. |
| `TASKY_COOKIE_SECURE` | `true` em produção; `false` permite testar em `http://localhost`. |
| `TASKY_CORS_ORIGEM` | Origem permitida. Vazio = sem CORS (caso de origem única). |

Tudo isso vira um `@ConfigurationProperties` (record) validado na subida.

**`application.yml`:** adicionar `server.forward-headers-strategy: framework`, para que atrás do
proxy da hospedagem o IP do cliente chegue correto — sem isso o rate limit enxergaria só o IP do
balanceador e limitaria todo mundo junto.

**Verificação:** aplicação sobe; sobe recusando iniciar se `TASKY_JWT_SECRET` for curto demais.

---

## Etapa 2 — Serviço do access token

`TokenAcessoService` com duas operações: gerar a partir de um `Usuario` e validar devolvendo o id.

- JWT **HS256**, `sub` = id do usuário, claims `email` e `nome`, validade ~20 min.
- `issuer` fixo (`tasky`), conferido na validação.
- Usa o bean **`Clock`** — nada de `Instant.now()` solto.

**Testes unitários** com `Clock.fixed`: token válido volta o id; token expirado é rejeitado;
assinatura adulterada é rejeitada; token de outro `issuer` é rejeitado.

---

## Etapa 3 — Refresh token com rotação e família

`RefreshTokenService`, o coração da fatia.

- **Emissão:** 256 bits de aleatoriedade (`SecureRandom`), codificados em base64url. O valor em
  claro só existe na resposta; no banco fica o **SHA-256**.
- **Família:** cada login abre uma `familia` (UUID). Todo token derivado por rotação herda a mesma
  família.
- **Rotação:** apresentar um token válido revoga aquele token e emite outro na mesma família.

**Os quatro desfechos possíveis de um refresh:**

| Situação | Resposta |
|---|---|
| Token válido e não revogado | Rotaciona: novo access + novo refresh |
| Token revogado **há menos de 60s** | Corrida legítima entre abas → emite novos, **sem** revogar a família |
| Token revogado **há mais de 60s** | Replay: alguém reusou um token antigo → **revoga a família inteira** |
| Token inexistente ou expirado | 401, sem revogar nada |

A janela de graça é o que impede que duas abas atualizando ao mesmo tempo derrubem a sessão — e é
justamente o caso que o spec exige tratar.

- **Logout:** revoga a família atual.
- **Limpeza:** job `@Scheduled` diário apagando tokens expirados há mais de 30 dias.

**Testes de integração** (Testcontainers): rotação encadeada funciona; replay revoga a família e
invalida os demais; dentro da graça não revoga; token expirado é recusado; logout invalida.

---

## Etapa 4 — Filtro JWT e configuração de segurança

- `JwtAuthenticationFilter` (`OncePerRequestFilter`): lê o header `Authorization: Bearer`, valida e
  popula o `SecurityContext`. Sem header, segue adiante — quem barra é a regra de autorização.
- `SecurityConfig`:
  - sessão **stateless**, `PasswordEncoder` BCrypt;
  - público: `/api/v1/auth/**`, `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`;
  - todo o resto exige autenticação;
  - CORS só quando `TASKY_CORS_ORIGEM` estiver preenchida.

**CSRF — o ponto delicado desta fatia.** O `/refresh` se autentica por **cookie**, que é exatamente
o padrão vulnerável a CSRF. Com `SameSite=Lax` o navegador já barra o POST cross-site, mas como
decidimos deixar a política configurável, o modo `SameSite=None` (origens separadas) ficaria
exposto. Por isso o `/refresh` **exige o header `X-Tasky-Client: web`**: um formulário malicioso em
outro site não consegue enviar header customizado sem passar por preflight de CORS, que nós não
autorizamos. É uma defesa simples e eficaz, e vale nas duas configurações.

**Verificação:** requisição sem token a uma rota protegida devolve 401; com token válido, 200;
`/refresh` sem o header customizado é recusado.

---

## Etapa 5 — Rate limit no login

`LoginRateLimitFilter`: janela deslizante em memória, ~10 tentativas por minuto por IP, aplicada
só a `POST /api/v1/auth/login`. Excedeu → **429** com `Retry-After`. Usa o `Clock` injetado e limpa
entradas velhas para não vazar memória.

**Teste:** 10 tentativas passam, a 11ª devolve 429; após avançar o relógio, libera de novo.

---

## Etapa 6 — Endpoints

Todos sob `/api/v1/auth`. DTOs como `record` com Bean Validation.

| Método | Rota | Comportamento |
|---|---|---|
| POST | `/registrar` | Exige `codigoConvite`. 201 em caso de sucesso. Desabilitado se a env var não estiver definida. |
| POST | `/login` | Devolve access token no corpo e refresh no cookie `httpOnly`. |
| POST | `/refresh` | Lê o cookie, aplica a rotação da Etapa 3, devolve novo access e novo cookie. |
| POST | `/logout` | Revoga a família e limpa o cookie. |
| GET | `/eu` | Dados do usuário autenticado. |

**Cookie:** nome `tasky_refresh`, `httpOnly`, `Secure` e `SameSite` vindos de configuração,
`Path=/api/v1/auth` (o cookie não é enviado nas demais chamadas), validade ~60 dias.

**Login não pode revelar se o e-mail existe:** quando o usuário não for encontrado, ainda assim
comparar a senha contra um hash descartável, para que o tempo de resposta não denuncie a diferença,
e devolver sempre a mesma mensagem genérica.

**Tratamento de erros:** `@ControllerAdvice` devolvendo `400` com `{ campo, mensagem }[]`, conforme
o spec. Como esta é a primeira superfície de API real do projeto, o handler nasce aqui e passa a
valer para as fatias seguintes.

---

## Etapa 7 — Frontend: serviço, interceptor e guard

- **`AuthService`:** guarda o access token **em memória** (nunca em `localStorage`, que é
  legível por qualquer script) e expõe o usuário logado. Como o token vive só em memória, **todo
  reload precisa chamar `/refresh`** para restaurar a sessão — consequência esperada do desenho.
- **Restauração na inicialização:** um inicializador de aplicação tenta o refresh antes de renderizar,
  evitando o "pisca-pisca" de cair no login e voltar. (Confirmar a API atual do Angular 22 na hora.)
- **`interceptorAutenticacao`:** anexa o `Bearer`; ao receber **401**, chama o refresh e repete a
  requisição **uma única vez**. Se o refresh falhar, limpa a sessão e manda para `/login`.
  **Requisições concorrentes compartilham o mesmo refresh em andamento** — sem isso, cinco chamadas
  falhando juntas disparariam cinco rotações e o detector de replay derrubaria a sessão. Este é o
  detalhe mais fácil de errar da fatia.
- **`guardAutenticacao`:** protege as rotas e preserva o `returnUrl`.

**Testes:** 401 dispara refresh e repete uma vez; refresh falhando desloga; requisições concorrentes
disparam **um único** refresh; o guard redireciona preservando o destino.

---

## Etapa 8 — Tela de login e rotas protegidas

- `/login` pública, com formulário reativo (e-mail e senha), mensagens de erro e estado de carregando.
- As quatro abas passam a exigir o guard.
- Botão de sair no shell — provisório, até existir a tela de Ajustes.
- A navegação some quando não há sessão (a tela de login ocupa a página inteira).

**Verificação manual:** acessar `/hoje` deslogado leva ao login; após entrar, volta para `/hoje`;
recarregar mantém a sessão; sair devolve ao login e o botão "voltar" do navegador não recupera a
sessão.

---

## Etapa 9 — Teste de integração ponta a ponta

Um teste com Testcontainers cobrindo o caminho completo: registrar com convite → logar → acessar
rota protegida → refresh rotacionando → replay do token antigo revogando a família → logout
invalidando tudo.

**Verificação:** `mvn verify` verde e CI verde no PR.

---

## Ordem de execução e pontos de parada

```
1. Dependencias + configuracao ─── backend sobe com as novas envs
2. Servico do access token ─────── testes unitarios verdes
3. Refresh com rotacao/familia ─── testes de rotacao, replay e graca verdes
4. Filtro JWT + seguranca ──────── rota protegida devolve 401 sem token
5. Rate limit ──────────────────── 11a tentativa devolve 429
6. Endpoints ──────────────────── consigo registrar e logar via HTTP
7. Front: servico/interceptor/guard  testes do front verdes
8. Login + rotas protegidas ────── ponto de parada: fluxo completo no navegador
9. Teste ponta a ponta ─────────── FATIA 2 CONCLUIDA
```

**Branch:** `feature/fatia-2-autenticacao`, PR para `main`, merge só com CI verde.

---

## Riscos desta fatia

| Risco | Probabilidade | Contenção |
|---|---|---|
| Refreshes concorrentes derrubando a sessão | **alta** se o interceptor for ingênuo | Compartilhar o refresh em andamento no front + janela de graça no back. Existe teste para os dois lados. |
| Cookie não chegar ao backend em dev | média | O proxy do `ng serve` mantém a origem única; se falhar, conferir `Path` e `Secure` (com `Secure=true` o cookie não vai por `http://localhost`). |
| `SameSite=None` sem `Secure` | média | Navegadores rejeitam a combinação. A validação da configuração recusa esse par na subida. |
| Segredo JWT fraco ou versionado | média | Mínimo de 32 bytes validado na subida; segredo só em env var, nunca no repositório. |
| Rate limit bloqueando o próprio autor | baixa | 10/min é folgado para uso humano; o 429 informa `Retry-After`. |
| Perder acesso por bug na rotação | média | Enquanto a fatia não fecha, manter um caminho de reset de senha por linha de comando no banco local. |

---

## Próxima fatia

**Fatia 3 — Rotina + `Hoje` (esqueleto):** CRUD de `ModeloDia` e `BlocoModelo`, atribuição dos dias
da semana, e a linha do tempo do dia montada no servidor a partir do fuso do usuário.
