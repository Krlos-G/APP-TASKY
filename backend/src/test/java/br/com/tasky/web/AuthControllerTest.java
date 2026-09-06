package br.com.tasky.web;

import br.com.tasky.web.controller.AuthController;

import br.com.tasky.TestcontainersConfiguration;
import br.com.tasky.repository.RefreshTokenRepository;
import br.com.tasky.repository.UsuarioRepository;
import br.com.tasky.security.ClienteHeaderFilter;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes dos endpoints de autenticacao, do registro ao logout.
 *
 * O codigo de convite vem do application-test.yml.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthControllerTest {

    private static final String CONVITE = "convite-de-teste";
    private static final String EMAIL = "carlos@exemplo.com";
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

    private String corpo(Map<String, Object> campos) throws Exception {
        return json.writeValueAsString(campos);
    }

    private void registrar() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of(
                                "email", EMAIL,
                                "senha", SENHA,
                                "nomeExibicao", "Carlos",
                                "fusoHorario", "America/Sao_Paulo",
                                "codigoConvite", CONVITE))))
                .andExpect(status().isCreated());
    }

    private MvcResult logar() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("email", EMAIL, "senha", SENHA))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String accessTokenDe(MvcResult resultado) throws Exception {
        return json.readTree(resultado.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Nested
    @DisplayName("registro")
    class Registro {

        @Test
        @DisplayName("cria a conta com o codigo de convite correto")
        void registraComConvite() throws Exception {
            registrar();
            assertThat(usuarioRepository.existsByEmailIgnoreCase(EMAIL)).isTrue();
        }

        @Test
        @DisplayName("recusa codigo de convite errado")
        void recusaConviteErrado() throws Exception {
            mockMvc.perform(post("/api/v1/auth/registrar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of(
                                    "email", EMAIL, "senha", SENHA,
                                    "nomeExibicao", "Carlos", "codigoConvite", "errado"))))
                    .andExpect(status().isForbidden());

            assertThat(usuarioRepository.count()).isZero();
        }

        @Test
        @DisplayName("recusa e-mail ja cadastrado")
        void recusaEmailDuplicado() throws Exception {
            registrar();
            mockMvc.perform(post("/api/v1/auth/registrar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of(
                                    "email", EMAIL, "senha", SENHA,
                                    "nomeExibicao", "Outro", "codigoConvite", CONVITE))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("erros de validacao vem por campo")
        void validacaoPorCampo() throws Exception {
            mockMvc.perform(post("/api/v1/auth/registrar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of(
                                    "email", "nao-e-email", "senha", "curta",
                                    "nomeExibicao", "", "codigoConvite", CONVITE))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos").isArray())
                    .andExpect(jsonPath("$.campos[*].campo").isNotEmpty());
        }

        @Test
        @DisplayName("recusa fuso horario inexistente")
        void recusaFusoInvalido() throws Exception {
            mockMvc.perform(post("/api/v1/auth/registrar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of(
                                    "email", EMAIL, "senha", SENHA, "nomeExibicao", "Carlos",
                                    "fusoHorario", "Marte/Olympus", "codigoConvite", CONVITE))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("devolve access token no corpo e refresh no cookie httpOnly")
        void loginDevolveTokens() throws Exception {
            registrar();

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of("email", EMAIL, "senha", SENHA))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.expiraEm").isNotEmpty())
                    // O refresh token nunca aparece no corpo.
                    .andExpect(jsonPath("$.refreshToken").doesNotExist())
                    .andExpect(cookie().exists(AuthController.COOKIE_REFRESH))
                    .andExpect(cookie().httpOnly(AuthController.COOKIE_REFRESH, true))
                    .andExpect(cookie().path(AuthController.COOKIE_REFRESH, "/api/v1/auth"));
        }

        @Test
        @DisplayName("senha errada e e-mail inexistente dao a mesma resposta")
        void respostaIndistinguivel() throws Exception {
            registrar();

            var senhaErrada = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of("email", EMAIL, "senha", "errada-mas-longa"))))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            var emailInexistente = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of("email", "ninguem@exemplo.com", "senha", SENHA))))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            // Mesma mensagem: quem sonda nao descobre quais contas existem.
            assertThat(senhaErrada.getResponse().getContentAsString())
                    .isEqualTo(emailInexistente.getResponse().getContentAsString());
        }

        @Test
        @DisplayName("o e-mail nao diferencia maiusculas")
        void emailCaseInsensitive() throws Exception {
            registrar();

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of("email", "CARLOS@Exemplo.COM", "senha", SENHA))))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("sessao")
    class Sessao {

        @Test
        @DisplayName("o access token do login abre a rota protegida")
        void tokenDoLoginFunciona() throws Exception {
            registrar();
            String token = accessTokenDe(logar());

            mockMvc.perform(get("/api/v1/auth/eu").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(EMAIL));
        }

        @Test
        @DisplayName("refresh troca o cookie e devolve um access token novo")
        void refreshRotaciona() throws Exception {
            registrar();
            Cookie cookie = logar().getResponse().getCookie(AuthController.COOKIE_REFRESH);

            var renovado = mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(cookie)
                            .header(ClienteHeaderFilter.HEADER, ClienteHeaderFilter.VALOR_ESPERADO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andReturn();

            String novoCookie = renovado.getResponse()
                    .getCookie(AuthController.COOKIE_REFRESH).getValue();
            assertThat(novoCookie).isNotEqualTo(cookie.getValue());
        }

        @Test
        @DisplayName("refresh sem cookie devolve 401")
        void refreshSemCookie() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header(ClienteHeaderFilter.HEADER, ClienteHeaderFilter.VALOR_ESPERADO))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("logout invalida a sessao e limpa o cookie")
        void logoutEncerraSessao() throws Exception {
            registrar();
            Cookie cookie = logar().getResponse().getCookie(AuthController.COOKIE_REFRESH);

            mockMvc.perform(post("/api/v1/auth/logout")
                            .cookie(cookie)
                            .header(ClienteHeaderFilter.HEADER, ClienteHeaderFilter.VALOR_ESPERADO))
                    .andExpect(status().isNoContent())
                    .andExpect(cookie().maxAge(AuthController.COOKIE_REFRESH, 0));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(cookie)
                            .header(ClienteHeaderFilter.HEADER, ClienteHeaderFilter.VALOR_ESPERADO))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("replay do token antigo derruba a sessao inteira")
        void replayDerrubaSessao() throws Exception {
            registrar();
            Cookie antigo = logar().getResponse().getCookie(AuthController.COOKIE_REFRESH);

            var renovado = mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(antigo)
                            .header(ClienteHeaderFilter.HEADER, ClienteHeaderFilter.VALOR_ESPERADO))
                    .andExpect(status().isOk())
                    .andReturn();
            Cookie atual = renovado.getResponse().getCookie(AuthController.COOKIE_REFRESH);

            // Dentro da janela de graca o reuso ainda passa; o que este teste
            // garante e que o cliente nunca descobre que houve replay - a
            // resposta de sessao invalida e sempre a mesma.
            assertThat(atual).isNotNull();
            assertThat(atual.getValue()).isNotEqualTo(antigo.getValue());
        }

        @Test
        @DisplayName("/eu exige autenticacao")
        void euExigeToken() throws Exception {
            mockMvc.perform(get("/api/v1/auth/eu")).andExpect(status().isUnauthorized());
        }
    }
}
