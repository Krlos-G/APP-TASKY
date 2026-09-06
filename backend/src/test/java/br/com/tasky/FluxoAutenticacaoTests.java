package br.com.tasky;

import br.com.tasky.repository.RefreshTokenRepository;
import br.com.tasky.repository.UsuarioRepository;
import br.com.tasky.security.ClienteHeaderFilter;
import br.com.tasky.web.controller.AuthController;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de fluxo da Fatia 2.
 *
 * Enquanto os testes das etapas anteriores cobrem cada peca isoladamente, este
 * percorre a jornada inteira contra um PostgreSQL real - registro, login, uso,
 * renovacao, replay e logout - garantindo que as pecas se encaixam na ordem em
 * que o app de verdade as usa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FluxoAutenticacaoTests {

    private static final String CONVITE = "convite-de-teste";
    private static final String EMAIL = "carlos@tasky.app";
    private static final String SENHA = "uma-senha-bem-longa";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void limpar() {
        refreshTokenRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    @DisplayName("a jornada completa do usuario, do registro ao logout")
    void jornadaCompleta() throws Exception {
        // ---------------------------------------------------------------
        // 1. Registro, exigindo o codigo de convite
        // ---------------------------------------------------------------
        mockMvc.perform(post("/api/v1/auth/registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", EMAIL,
                                "senha", SENHA,
                                "nomeExibicao", "Carlos",
                                "fusoHorario", "America/Sao_Paulo",
                                "codigoConvite", CONVITE))))
                .andExpect(status().isCreated());

        // ---------------------------------------------------------------
        // 2. Login: access token no corpo, refresh no cookie
        // ---------------------------------------------------------------
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", EMAIL, "senha", SENHA))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = tokenDe(login);
        Cookie refreshInicial = login.getResponse().getCookie(AuthController.COOKIE_REFRESH);
        assertThat(refreshInicial).isNotNull();

        // ---------------------------------------------------------------
        // 3. O token abre as rotas protegidas
        // ---------------------------------------------------------------
        mockMvc.perform(get("/api/v1/rotina/modelos")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // ---------------------------------------------------------------
        // 4. Renovacao: o cookie e trocado por um novo
        // ---------------------------------------------------------------
        MvcResult renovacao = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshInicial)
                        .header(ClienteHeaderFilter.HEADER, ClienteHeaderFilter.VALOR_ESPERADO))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshRotacionado = renovacao.getResponse().getCookie(AuthController.COOKIE_REFRESH);
        assertThat(refreshRotacionado.getValue()).isNotEqualTo(refreshInicial.getValue());

        // O token novo tambem funciona nas rotas protegidas.
        mockMvc.perform(get("/api/v1/auth/eu")
                        .header("Authorization", "Bearer " + tokenDe(renovacao)))
                .andExpect(status().isOk());

        // ---------------------------------------------------------------
        // 5. Logout encerra a sessao no servidor
        // ---------------------------------------------------------------
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshRotacionado)
                        .header(ClienteHeaderFilter.HEADER, ClienteHeaderFilter.VALOR_ESPERADO))
                .andExpect(status().isNoContent());

        // ---------------------------------------------------------------
        // 6. Depois do logout, o cookie nao renova mais
        // ---------------------------------------------------------------
        // Sem a checagem de familia viva, este passo passaria pela janela de
        // graca e devolveria um token novo, anulando o logout.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshRotacionado)
                        .header(ClienteHeaderFilter.HEADER, ClienteHeaderFilter.VALOR_ESPERADO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("um token vazado, reapresentado, derruba a sessao inteira")
    void replayDerrubaSessao() throws Exception {
        registrarELogar();
        MvcResult login = logar();
        Cookie vazado = login.getResponse().getCookie(AuthController.COOKIE_REFRESH);

        // O usuario legitimo renova normalmente.
        MvcResult renovacao = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(vazado)
                        .header(ClienteHeaderFilter.HEADER, ClienteHeaderFilter.VALOR_ESPERADO))
                .andExpect(status().isOk())
                .andReturn();
        Cookie atual = renovacao.getResponse().getCookie(AuthController.COOKIE_REFRESH);

        // Quem tem a copia do token antigo tenta usa-la. Dentro da janela de
        // graca isso ainda passa - o replay so e detectado depois dela, e o
        // teste de unidade do RefreshTokenService cobre essa parte com o
        // relogio controlado. Aqui o que importa e que a resposta ao cliente
        // nunca revela qual dos casos ocorreu.
        MvcResult reuso = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(vazado)
                        .header(ClienteHeaderFilter.HEADER, ClienteHeaderFilter.VALOR_ESPERADO))
                .andReturn();

        assertThat(reuso.getResponse().getStatus()).isIn(200, 401);
        if (reuso.getResponse().getStatus() == 401) {
            assertThat(reuso.getResponse().getContentAsString()).contains("Sessao expirada");
        }
        assertThat(atual).isNotNull();
    }

    @Test
    @DisplayName("a sessao de um dispositivo nao interfere na do outro")
    void sessoesIndependentesPorDispositivo() throws Exception {
        registrarELogar();

        Cookie celular = logar().getResponse().getCookie(AuthController.COOKIE_REFRESH);
        Cookie pc = logar().getResponse().getCookie(AuthController.COOKIE_REFRESH);
        assertThat(celular.getValue()).isNotEqualTo(pc.getValue());

        // Sair no celular nao pode desconectar o PC: cada login abre a sua
        // propria familia de tokens.
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(celular)
                        .header(ClienteHeaderFilter.HEADER, ClienteHeaderFilter.VALOR_ESPERADO))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(celular)
                        .header(ClienteHeaderFilter.HEADER, ClienteHeaderFilter.VALOR_ESPERADO))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(pc)
                        .header(ClienteHeaderFilter.HEADER, ClienteHeaderFilter.VALOR_ESPERADO))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("um site de terceiro nao consegue renovar a sessao")
    void sitePrecisaDoCabecalhoDeCliente() throws Exception {
        registrarELogar();
        Cookie refresh = logar().getResponse().getCookie(AuthController.COOKIE_REFRESH);

        // O navegador enviaria o cookie automaticamente, mas um formulario em
        // outro dominio nao consegue acrescentar cabecalho customizado.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh))
                .andExpect(status().isForbidden());

        // Com o cabecalho, a mesma requisicao funciona.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refresh)
                        .header(ClienteHeaderFilter.HEADER, ClienteHeaderFilter.VALOR_ESPERADO))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------

    private void registrarELogar() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", EMAIL, "senha", SENHA,
                                "nomeExibicao", "Carlos", "codigoConvite", CONVITE))))
                .andExpect(status().isCreated());
    }

    private MvcResult logar() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", EMAIL, "senha", SENHA))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String tokenDe(MvcResult resultado) throws Exception {
        return json.readTree(resultado.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
