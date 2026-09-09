package br.com.tasky;

import br.com.tasky.entity.Usuario;
import br.com.tasky.repository.HabitoRepository;
import br.com.tasky.repository.RefreshTokenRepository;
import br.com.tasky.repository.RegistroHabitoRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de fluxo da Fatia 4.
 *
 * As etapas anteriores testam cada peca isolada; aqui o relogio anda de
 * verdade, dia apos dia, que e a unica forma de provar que o streak se comporta
 * ao longo do tempo e nao apenas num instante congelado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, FluxoHabitosTests.RelogioDeTesteConfig.class})
class FluxoHabitosTests {

    /** Segunda-feira, 7 de setembro de 2026, 12:00 em Sao Paulo. */
    private static final Instant SEGUNDA = Instant.parse("2026-09-07T15:00:00Z");

    @TestConfiguration
    static class RelogioDeTesteConfig {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return new RelogioAjustavel(SEGUNDA);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private HabitoRepository habitoRepository;
    @Autowired private RegistroHabitoRepository registroRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private TokenAcessoService tokenAcessoService;
    @Autowired private Clock clock;

    private RelogioAjustavel relogio;
    private Usuario usuario;
    private String token;

    @BeforeEach
    void preparar() {
        relogio = (RelogioAjustavel) clock;
        relogio.definir(SEGUNDA);

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
    @DisplayName("marco tres dias seguidos e vejo streak 3")
    void tresDiasSeguidos() throws Exception {
        // -----------------------------------------------------------
        // 1. Habito novo: nada marcado, nenhuma sequencia
        // -----------------------------------------------------------
        long id = criarDiario("Ler");

        mockMvc.perform(autenticado(get("/api/v1/dia")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habitos[0].nome").value("Ler"))
                .andExpect(jsonPath("$.habitos[0].streak").value(0))
                .andExpect(jsonPath("$.habitos[0].status").doesNotExist());

        // -----------------------------------------------------------
        // 2. Segunda, terca, quarta - um dia de cada vez
        // -----------------------------------------------------------
        marcar(id, "2026-09-07", "FEITO").andExpect(jsonPath("$.streak").value(1));

        agoraE("2026-09-08T15:00:00Z");
        marcar(id, "2026-09-08", "FEITO").andExpect(jsonPath("$.streak").value(2));

        agoraE("2026-09-09T15:00:00Z");
        marcar(id, "2026-09-09", "FEITO").andExpect(jsonPath("$.streak").value(3));

        assertThat(streakDe(id)).isEqualTo(3);

        // -----------------------------------------------------------
        // 3. Desmarcar o dia do meio quebra a corrente ali
        // -----------------------------------------------------------
        mockMvc.perform(autenticado(delete("/api/v1/habitos/" + id + "/registros/2026-09-08")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streak").value(1));

        assertThat(streakDe(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("o dia que ainda nao acabou nao derruba a sequencia")
    void diaEmAbertoNaoQuebra() throws Exception {
        long id = criarDiario("Ler");

        marcar(id, "2026-09-07", "FEITO").andExpect(status().isOk());
        agoraE("2026-09-08T15:00:00Z");
        marcar(id, "2026-09-08", "FEITO").andExpect(jsonPath("$.streak").value(2));

        // Amanhece a quarta e o habito ainda nao foi feito. O dia esta em
        // aberto: sem esta regra o numero cairia a zero toda meia-noite, que e
        // o oposto do efeito que o streak existe para ter.
        agoraE("2026-09-09T09:00:00Z");
        assertThat(streakDe(id)).isEqualTo(2);

        mockMvc.perform(autenticado(get("/api/v1/dia")))
                .andExpect(jsonPath("$.habitos[0].streak").value(2))
                .andExpect(jsonPath("$.habitos[0].status").doesNotExist());

        // So quando a quarta passa em branco e que a corrente quebra.
        agoraE("2026-09-10T09:00:00Z");
        assertThat(streakDe(id)).isZero();
    }

    @Test
    @DisplayName("habito de terca e quinta atravessa os dias que nao sao dele")
    void agendaPorDiasDaSemana() throws Exception {
        long id = criarPorDias("Academia", "TER", "QUI");

        // Segunda nao e dia dele: nem aparece no dia.
        mockMvc.perform(autenticado(get("/api/v1/dia")))
                .andExpect(jsonPath("$.habitos.length()").value(0));

        agoraE("2026-09-08T15:00:00Z");
        marcar(id, "2026-09-08", "FEITO").andExpect(jsonPath("$.streak").value(1));

        // Quarta nao cobra nada - e nao quebra nada.
        agoraE("2026-09-09T15:00:00Z");
        assertThat(streakDe(id)).isEqualTo(1);

        agoraE("2026-09-10T15:00:00Z");
        marcar(id, "2026-09-10", "FEITO").andExpect(jsonPath("$.streak").value(2));
    }

    @Test
    @DisplayName("pular preserva a corrente, faltar quebra")
    void pularNaoQuebra() throws Exception {
        long id = criarDiario("Ler");

        marcar(id, "2026-09-07", "FEITO").andExpect(status().isOk());

        agoraE("2026-09-08T15:00:00Z");
        marcar(id, "2026-09-08", "PULADO").andExpect(jsonPath("$.streak").value(1));

        agoraE("2026-09-09T15:00:00Z");
        marcar(id, "2026-09-09", "FEITO").andExpect(jsonPath("$.streak").value(2));
    }

    // ------------------------------------------------------------------

    private MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder req) {
        return req.header("Authorization", "Bearer " + token);
    }

    /**
     * Viajar no tempo tambem envelhece o access token, entao ele e reemitido -
     * senao a requisicao voltaria 401 por um motivo que nao e o investigado.
     */
    private void agoraE(String instante) {
        relogio.definir(Instant.parse(instante));
        token = tokenAcessoService.gerar(usuario).valor();
    }

    private long criarDiario(String nome) throws Exception {
        return criar(Map.of("nome", nome, "tipoAgenda", "DIARIO"));
    }

    private long criarPorDias(String nome, String... dias) throws Exception {
        return criar(Map.of("nome", nome, "tipoAgenda", "DIAS_SEMANA",
                "diasSemana", List.of(dias)));
    }

    private long criar(Map<String, Object> pedido) throws Exception {
        var resultado = mockMvc.perform(autenticado(post("/api/v1/habitos"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(pedido)))
                .andExpect(status().isCreated())
                .andReturn();

        return json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions marcar(
            long id, String data, String status) throws Exception {
        return mockMvc.perform(autenticado(put("/api/v1/habitos/" + id + "/registros/" + data))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", status))))
                .andExpect(status().isOk());
    }

    /** O streak como a tela de habitos o veria, e nao o da resposta da marcacao. */
    private int streakDe(long id) throws Exception {
        var resultado = mockMvc.perform(autenticado(get("/api/v1/habitos")))
                .andExpect(status().isOk())
                .andReturn();

        for (var no : json.readTree(resultado.getResponse().getContentAsString())) {
            if (no.get("id").asLong() == id) {
                return no.get("streak").asInt();
            }
        }
        throw new AssertionError("Habito " + id + " nao esta na lista");
    }
}
