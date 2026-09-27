package br.com.tasky.web;

import br.com.tasky.TestcontainersConfiguration;
import br.com.tasky.config.PushProperties;
import br.com.tasky.entity.Usuario;
import br.com.tasky.notificacao.CanalDeTeste;
import br.com.tasky.notificacao.ResultadoEnvio;
import br.com.tasky.repository.InscricaoPushRepository;
import br.com.tasky.repository.RefreshTokenRepository;
import br.com.tasky.repository.UsuarioRepository;
import br.com.tasky.security.TokenAcessoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, InscricaoPushControllerTest.CanalDeTesteConfig.class})
class InscricaoPushControllerTest {

    @TestConfiguration
    static class CanalDeTesteConfig {
        @Bean
        @Primary
        CanalDeTeste canalDeTeste() {
            return new CanalDeTeste();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private InscricaoPushRepository inscricaoRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private TokenAcessoService tokenAcessoService;
    @Autowired private PushProperties propriedades;
    @Autowired private CanalDeTeste canal;

    private String tokenCarlos;
    private String tokenOutro;

    @BeforeEach
    void preparar() {
        canal.limpar();

        inscricaoRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        usuarioRepository.deleteAll();

        tokenCarlos = tokenDe(criarUsuario("carlos@tasky.app"));
        tokenOutro = tokenDe(criarUsuario("outro@tasky.app"));
    }

    @Test
    @DisplayName("a chave publica vem junto com o aviso de que push esta ligado")
    void chavePublica() throws Exception {
        mockMvc.perform(comAuth(get("/api/v1/inscricoes-push/chave"), tokenCarlos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitado").value(propriedades.habilitado()))
                .andExpect(jsonPath("$.chavePublica").value(propriedades.chavePublica()));
    }

    @Test
    @DisplayName("registrar guarda o aparelho")
    void registrar() throws Exception {
        registrar(tokenCarlos, "https://web.push.apple.com/aaa").andExpect(status().isNoContent());

        assertThat(inscricaoRepository.findAll())
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.getEndpoint()).isEqualTo("https://web.push.apple.com/aaa");
                    assertThat(i.getUserAgent()).isEqualTo("iPhone");
                    assertThat(i.getVistoEm()).isNotNull();
                });
    }

    @Test
    @DisplayName("reinscrever o mesmo aparelho atualiza em vez de duplicar")
    void reinscrever() throws Exception {
        registrar(tokenCarlos, "https://web.push.apple.com/aaa").andExpect(status().isNoContent());

        // O navegador renova a inscricao e manda chaves novas para o mesmo endpoint.
        mockMvc.perform(comAuth(post("/api/v1/inscricoes-push"), tokenCarlos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "endpoint", "https://web.push.apple.com/aaa",
                                "p256dh", "chave-nova",
                                "auth", "segredo-novo",
                                "userAgent", "iPhone"))))
                .andExpect(status().isNoContent());

        assertThat(inscricaoRepository.findAll())
                .singleElement()
                .satisfies(i -> assertThat(i.getP256dh()).isEqualTo("chave-nova"));
    }

    @Test
    @DisplayName("remover desativa o aparelho, e remover de novo nao e erro")
    void remover() throws Exception {
        registrar(tokenCarlos, "https://web.push.apple.com/aaa").andExpect(status().isNoContent());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(comAuth(delete("/api/v1/inscricoes-push"), tokenCarlos)
                            .param("endpoint", "https://web.push.apple.com/aaa"))
                    .andExpect(status().isNoContent());
        }

        assertThat(inscricaoRepository.count()).isZero();
    }

    @Test
    @DisplayName("o teste sai para todos os aparelhos da conta")
    void testarEnviaParaTodos() throws Exception {
        registrar(tokenCarlos, "https://web.push.apple.com/iphone");
        registrar(tokenCarlos, "https://fcm.googleapis.com/wp/pc");
        registrar(tokenOutro, "https://web.push.apple.com/alheio");

        mockMvc.perform(comAuth(post("/api/v1/inscricoes-push/testar"), tokenCarlos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enviadas").value(2))
                .andExpect(jsonPath("$.aparelhos").value(2));

        assertThat(canal.destinos).hasSize(2)
                .allSatisfy(destino -> assertThat(destino).doesNotContain("alheio"));
    }

    @Test
    @DisplayName("aparelho que sumiu e apagado em vez de ficar tentando")
    void inscricaoExpiradaSaiDoBanco() throws Exception {
        registrar(tokenCarlos, "https://web.push.apple.com/velho");
        registrar(tokenCarlos, "https://web.push.apple.com/novo");

        canal.resposta = inscricao -> inscricao.getEndpoint().endsWith("velho")
                ? ResultadoEnvio.INSCRICAO_EXPIRADA
                : ResultadoEnvio.ENVIADO;

        mockMvc.perform(comAuth(post("/api/v1/inscricoes-push/testar"), tokenCarlos))
                .andExpect(jsonPath("$.enviadas").value(1))
                .andExpect(jsonPath("$.aparelhos").value(2));

        assertThat(inscricaoRepository.findAll())
                .singleElement()
                .satisfies(i -> assertThat(i.getEndpoint()).endsWith("novo"));
    }

    @Test
    @DisplayName("sem aparelho nenhum, o teste responde zero em vez de falhar")
    void testarSemAparelhos() throws Exception {
        mockMvc.perform(comAuth(post("/api/v1/inscricoes-push/testar"), tokenCarlos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enviadas").value(0))
                .andExpect(jsonPath("$.aparelhos").value(0));
    }

    @Test
    @DisplayName("nao da para desativar o aparelho de outra conta")
    void removerAlheioNaoApaga() throws Exception {
        registrar(tokenOutro, "https://web.push.apple.com/alheio");

        mockMvc.perform(comAuth(delete("/api/v1/inscricoes-push"), tokenCarlos)
                        .param("endpoint", "https://web.push.apple.com/alheio"))
                .andExpect(status().isNoContent());

        assertThat(inscricaoRepository.count()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ apoio

    private Usuario criarUsuario(String email) {
        var usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setSenhaHash("nao-importa");
        usuario.setNomeExibicao(email);
        usuario.setFusoHorario("America/Sao_Paulo");
        return usuarioRepository.save(usuario);
    }

    private String tokenDe(Usuario usuario) {
        return tokenAcessoService.gerar(usuario).valor();
    }

    private MockHttpServletRequestBuilder comAuth(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", "Bearer " + token);
    }

    private ResultActions registrar(String token, String endpoint) throws Exception {
        return mockMvc.perform(comAuth(post("/api/v1/inscricoes-push"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "endpoint", endpoint,
                        "p256dh", "chave-do-navegador",
                        "auth", "segredo-do-navegador",
                        "userAgent", "iPhone"))));
    }
}
