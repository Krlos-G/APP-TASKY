package br.com.tasky.security;

import br.com.tasky.TestcontainersConfiguration;
import br.com.tasky.entity.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica as regras de autorizacao e a defesa de CSRF dos endpoints que se
 * autenticam por cookie.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenAcessoService tokenAcessoService;

    private String tokenValido() {
        var usuario = new Usuario();
        usuario.setId(7L);
        usuario.setEmail("carlos@exemplo.com");
        usuario.setNomeExibicao("Carlos");
        return tokenAcessoService.gerar(usuario).valor();
    }

    @Nested
    @DisplayName("rotas protegidas")
    class RotasProtegidas {

        @Test
        @DisplayName("sem token devolve 401, e nao um redirecionamento para login")
        void semTokenDevolve401() throws Exception {
            mockMvc.perform(get("/api/v1/ping"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("com token valido devolve os dados do usuario autenticado")
        void comTokenValidoDevolve200() throws Exception {
            mockMvc.perform(get("/api/v1/ping")
                            .header("Authorization", "Bearer " + tokenValido()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.usuarioId").value(7))
                    .andExpect(jsonPath("$.email").value("carlos@exemplo.com"));
        }

        @Test
        @DisplayName("token assinado por outra chave nao autentica")
        void tokenForjadoNaoAutentica() throws Exception {
            var outraChave = Keys.hmacShaKeyFor(
                    "chave-diferente-com-mais-de-32-bytes-de-tamanho".getBytes(StandardCharsets.UTF_8));
            String forjado = Jwts.builder()
                    .issuer("tasky")
                    .subject("7")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plusSeconds(600)))
                    .signWith(outraChave)
                    .compact();

            mockMvc.perform(get("/api/v1/ping").header("Authorization", "Bearer " + forjado))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("header malformado e tratado como ausencia de token")
        void headerMalformado() throws Exception {
            mockMvc.perform(get("/api/v1/ping").header("Authorization", "sem-o-prefixo-bearer"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/ping").header("Authorization", "Bearer "))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("rotas publicas")
    class RotasPublicas {

        @Test
        @DisplayName("o health check responde sem autenticacao")
        void healthEPublico() throws Exception {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("a documentacao da API responde sem autenticacao")
        void openApiEPublico() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("um token vencido nao impede o acesso a rota publica")
        void rotaPublicaIgnoraTokenRuim() throws Exception {
            mockMvc.perform(get("/actuator/health").header("Authorization", "Bearer lixo"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("defesa de CSRF nos endpoints com cookie")
    class DefesaCsrf {

        @Test
        @DisplayName("refresh sem o header de cliente e recusado com 403")
        void refreshSemHeaderERecusado() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("logout sem o header de cliente e recusado com 403")
        void logoutSemHeaderERecusado() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("com o header o pedido passa pelo filtro")
        void comHeaderPassaPeloFiltro() throws Exception {
            // Os endpoints ainda nao existem (Etapa 6), entao o esperado aqui e
            // 404: o que importa e nao ser mais o 403 do filtro.
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header(ClienteHeaderFilter.HEADER, ClienteHeaderFilter.VALOR_ESPERADO))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("o filtro nao interfere nos demais endpoints")
        void filtroNaoAfetaOutrasRotas() throws Exception {
            // Sem o header e sem token: deve ser 401 por falta de autenticacao,
            // nao 403 pelo filtro de cliente.
            mockMvc.perform(get("/api/v1/ping"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
