package br.com.tasky;

import br.com.tasky.entity.LembreteDia;
import br.com.tasky.entity.Usuario;
import br.com.tasky.entity.enums.StatusLembrete;
import br.com.tasky.notificacao.CanalDeTeste;
import br.com.tasky.repository.HabitoRepository;
import br.com.tasky.repository.InscricaoPushRepository;
import br.com.tasky.repository.LembreteDiaRepository;
import br.com.tasky.repository.RefreshTokenRepository;
import br.com.tasky.repository.RegistroHabitoRepository;
import br.com.tasky.repository.UsuarioRepository;
import br.com.tasky.security.TokenAcessoService;
import br.com.tasky.service.DespacharLembretesJob;
import br.com.tasky.service.MaterializarLembretesJob;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de fluxo da Fatia 7.
 *
 * Cadastro pela API, os dois jobs de verdade e o relogio andando ate a hora do
 * lembrete: e a unica forma de ver a corrente inteira funcionar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, FluxoLembretesTests.ConfigDeTeste.class})
class FluxoLembretesTests {

    /** Segunda-feira, 28 de setembro de 2026, 08:00 em Sao Paulo. */
    private static final Instant MANHA = Instant.parse("2026-09-28T11:00:00Z");

    /** 19:00 do mesmo dia, a hora do lembrete. */
    private static final String NOITE = "2026-09-28T22:00:00Z";

    @TestConfiguration
    static class ConfigDeTeste {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return new RelogioAjustavel(MANHA);
        }

        @Bean
        @Primary
        CanalDeTeste canalDeTeste() {
            return new CanalDeTeste();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private MaterializarLembretesJob materializacao;
    @Autowired private DespacharLembretesJob despacho;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private HabitoRepository habitoRepository;
    @Autowired private RegistroHabitoRepository registroRepository;
    @Autowired private LembreteDiaRepository lembreteRepository;
    @Autowired private InscricaoPushRepository inscricaoRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private TokenAcessoService tokenAcessoService;
    @Autowired private CanalDeTeste canal;
    @Autowired private Clock clock;

    private RelogioAjustavel relogio;
    private Usuario usuario;
    private String token;

    @BeforeEach
    void preparar() {
        relogio = (RelogioAjustavel) clock;
        relogio.definir(MANHA);
        canal.limpar();

        lembreteRepository.deleteAll();
        inscricaoRepository.deleteAll();
        registroRepository.deleteAll();
        habitoRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        usuarioRepository.deleteAll();

        var novo = new Usuario();
        novo.setEmail("carlos@tasky.app");
        novo.setSenhaHash("nao-importa");
        novo.setNomeExibicao("Carlos");
        novo.setFusoHorario("America/Sao_Paulo");
        usuario = usuarioRepository.save(novo);
        token = tokenAcessoService.gerar(usuario).valor();
    }

    @Test
    @DisplayName("do habito com lembrete ate a notificacao na hora certa")
    void jornadaCompleta() throws Exception {
        // -----------------------------------------------------------
        // 1. Ativo as notificacoes e crio o habito com lembrete
        // -----------------------------------------------------------
        inscreverAparelho();
        long habito = criarHabitoComLembrete("Ler", "19:00:00");

        // -----------------------------------------------------------
        // 2. O job da hora em hora materializa hoje e amanha
        // -----------------------------------------------------------
        materializacao.materializar();

        assertThat(lembreteRepository.findAll())
                .hasSize(2)
                .allSatisfy(l -> assertThat(l.getStatus()).isEqualTo(StatusLembrete.PENDENTE));

        // De manha nada vence ainda.
        despacho.despachar();
        assertThat(canal.enviadas).isEmpty();

        // -----------------------------------------------------------
        // 3. Chegam as 19:00 e a notificacao sai
        // -----------------------------------------------------------
        agoraE(NOITE);
        despacho.despachar();

        assertThat(canal.enviadas).singleElement()
                .satisfies(n -> {
                    assertThat(n.titulo()).isEqualTo("Ler");
                    assertThat(n.tag()).isEqualTo("HABITO-" + habito);
                });

        // -----------------------------------------------------------
        // 4. O proximo ciclo nao reenvia o que ja saiu
        // -----------------------------------------------------------
        despacho.despachar();
        assertThat(canal.enviadas).hasSize(1);

        assertThat(lembreteDeHoje().getStatus()).isEqualTo(StatusLembrete.ENVIADO);
    }

    @Test
    @DisplayName("quem ja fez o habito nao leva empurrao")
    void marcadoAntesNaoRecebe() throws Exception {
        inscreverAparelho();
        long habito = criarHabitoComLembrete("Ler", "19:00:00");
        materializacao.materializar();

        // Fiz de tarde, antes da hora do lembrete.
        mockMvc.perform(autenticado(put("/api/v1/habitos/" + habito + "/registros/2026-09-28"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FEITO\"}"))
                .andExpect(status().isOk());

        agoraE(NOITE);
        despacho.despachar();

        assertThat(canal.enviadas).isEmpty();
        assertThat(lembreteDeHoje().getStatus()).isEqualTo(StatusLembrete.CANCELADO);
    }

    @Test
    @DisplayName("servidor fora do ar na hora: o lembrete atrasado e descartado")
    void servidorForaDoAr() throws Exception {
        inscreverAparelho();
        criarHabitoComLembrete("Ler", "19:00:00");
        materializacao.materializar();

        // Voltou so as 20:00 - uma hora depois.
        agoraE("2026-09-28T23:00:00Z");
        despacho.despachar();

        assertThat(canal.enviadas).isEmpty();
        assertThat(lembreteDeHoje().getStatus()).isEqualTo(StatusLembrete.CANCELADO);
    }

    @Test
    @DisplayName("mudar a hora do lembrete reposiciona o envio")
    void mudarAHoraReposiciona() throws Exception {
        inscreverAparelho();
        long habito = criarHabitoComLembrete("Ler", "19:00:00");
        materializacao.materializar();

        // Passei para 21:00 antes de as 19:00 chegarem.
        mockMvc.perform(autenticado(put("/api/v1/habitos/" + habito))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Ler",
                                "tipoAgenda", "DIARIO",
                                "horaLembrete", "21:00:00"))))
                .andExpect(status().isOk());
        materializacao.materializar();

        agoraE(NOITE);
        despacho.despachar();
        assertThat(canal.enviadas).isEmpty();

        agoraE("2026-09-29T00:00:00Z");
        despacho.despachar();
        assertThat(canal.enviadas).hasSize(1);
    }

    // ------------------------------------------------------------------ apoio

    private MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder req) {
        return req.header("Authorization", "Bearer " + token);
    }

    private void agoraE(String instante) {
        relogio.definir(Instant.parse(instante));
        token = tokenAcessoService.gerar(usuario).valor();
    }

    private LembreteDia lembreteDeHoje() {
        return lembreteRepository.findAll().stream()
                .filter(l -> l.getDataRef().toString().equals("2026-09-28"))
                .findFirst()
                .orElseThrow();
    }

    private void inscreverAparelho() throws Exception {
        mockMvc.perform(autenticado(post("/api/v1/inscricoes-push"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "endpoint", "https://web.push.apple.com/iphone",
                                "p256dh", "chave-do-navegador",
                                "auth", "segredo",
                                "userAgent", "iPhone"))))
                .andExpect(status().isNoContent());
    }

    private long criarHabitoComLembrete(String nome, String hora) throws Exception {
        var resultado = mockMvc.perform(autenticado(post("/api/v1/habitos"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", nome,
                                "tipoAgenda", "DIARIO",
                                "horaLembrete", hora))))
                .andExpect(status().isCreated())
                .andReturn();

        return json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();
    }
}
