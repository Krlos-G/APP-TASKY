package br.com.tasky;

import br.com.tasky.entity.Usuario;
import br.com.tasky.repository.HabitoRepository;
import br.com.tasky.repository.RefreshTokenRepository;
import br.com.tasky.repository.RegistroHabitoRepository;
import br.com.tasky.repository.TarefaRepository;
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

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de fluxo da Fatia 6.
 *
 * O dia zera na virada; a semana nao. Provar isso exige o relogio andando de
 * verdade, um dia por vez, ate atravessar a semana inteira.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, FluxoResumoTests.RelogioDeTesteConfig.class})
class FluxoResumoTests {

    /** Segunda-feira, 21 de setembro de 2026, 12:00 em Sao Paulo. */
    private static final Instant SEGUNDA = Instant.parse("2026-09-21T15:00:00Z");

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
    @Autowired private TarefaRepository tarefaRepository;
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

        tarefaRepository.deleteAll();
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
    @DisplayName("o dia zera na virada, a semana guarda o que passou")
    void diaZeraSemanaGuarda() throws Exception {
        long habito = criarHabitoDiario("Ler");
        long tarefa = criarTarefa("Responder e-mail", "2026-09-21");

        // -----------------------------------------------------------
        // 1. Segunda de manha: nada feito ainda
        // -----------------------------------------------------------
        resumo()
                .andExpect(jsonPath("$.dia.habitosFeitos").value(0))
                .andExpect(jsonPath("$.dia.habitosDevidos").value(1))
                .andExpect(jsonPath("$.dia.tarefasFeitas").value(0))
                .andExpect(jsonPath("$.dia.tarefasDoDia").value(1))
                .andExpect(jsonPath("$.semana.habitosFeitos").value(0))
                .andExpect(jsonPath("$.semana.habitosCobrados").value(1))
                .andExpect(jsonPath("$.semana.tarefasConcluidas").value(0));

        // -----------------------------------------------------------
        // 2. Marco o habito e concluo a tarefa
        // -----------------------------------------------------------
        marcar(habito, "2026-09-21");
        concluir(tarefa);

        resumo()
                .andExpect(jsonPath("$.dia.habitosFeitos").value(1))
                .andExpect(jsonPath("$.dia.tarefasFeitas").value(1))
                .andExpect(jsonPath("$.semana.habitosFeitos").value(1))
                .andExpect(jsonPath("$.semana.tarefasConcluidas").value(1))
                .andExpect(jsonPath("$.streaks[0].streak").value(1));

        // -----------------------------------------------------------
        // 3. Terca: o dia comeca do zero, a semana lembra da segunda
        // -----------------------------------------------------------
        agoraE("2026-09-22T15:00:00Z");

        resumo()
                .andExpect(jsonPath("$.dia.habitosFeitos").value(0))
                .andExpect(jsonPath("$.dia.habitosDevidos").value(1))
                .andExpect(jsonPath("$.dia.tarefasDoDia").value(0))
                // A tarefa de ontem foi feita, entao nao virou atrasada.
                .andExpect(jsonPath("$.dia.atrasadas").value(0))
                .andExpect(jsonPath("$.semana.habitosFeitos").value(1))
                .andExpect(jsonPath("$.semana.habitosCobrados").value(2))
                .andExpect(jsonPath("$.semana.tarefasConcluidas").value(1))
                // O dia de hoje ainda em aberto nao derruba a sequencia.
                .andExpect(jsonPath("$.streaks[0].streak").value(1));
    }

    @Test
    @DisplayName("na segunda seguinte a semana recomeca do zero")
    void semanaRecomeca() throws Exception {
        long habito = criarHabitoDiario("Ler");
        long tarefa = criarTarefa("Responder e-mail", "2026-09-21");
        marcar(habito, "2026-09-21");
        concluir(tarefa);

        agoraE("2026-09-28T15:00:00Z");

        resumo()
                .andExpect(jsonPath("$.semana.inicio").value("2026-09-28"))
                .andExpect(jsonPath("$.semana.fim").value("2026-09-28"))
                .andExpect(jsonPath("$.semana.habitosFeitos").value(0))
                .andExpect(jsonPath("$.semana.habitosCobrados").value(1))
                .andExpect(jsonPath("$.semana.tarefasConcluidas").value(0));
    }

    @Test
    @DisplayName("trocar o fuso muda o resumo de dia junto")
    void fusoMudaODia() throws Exception {
        criarHabitoDiario("Ler");

        // 23:30 em Sao Paulo ainda e segunda; em Toquio ja e terca.
        agoraE("2026-09-22T02:30:00Z");
        resumo().andExpect(jsonPath("$.data").value("2026-09-21"));

        mockMvc.perform(autenticado(put("/api/v1/usuarios/eu/fuso"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fusoHorario\":\"Asia/Tokyo\"}"))
                .andExpect(status().isNoContent());

        resumo()
                .andExpect(jsonPath("$.data").value("2026-09-22"))
                .andExpect(jsonPath("$.semana.fim").value("2026-09-22"));
    }

    // ------------------------------------------------------------------

    private MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder req) {
        return req.header("Authorization", "Bearer " + token);
    }

    /** Viajar no tempo envelhece o access token: reemitir, senao volta 401. */
    private void agoraE(String instante) {
        relogio.definir(Instant.parse(instante));
        token = tokenAcessoService.gerar(usuario).valor();
    }

    private long criarHabitoDiario(String nome) throws Exception {
        var resultado = mockMvc.perform(autenticado(post("/api/v1/habitos"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("nome", nome, "tipoAgenda", "DIARIO"))))
                .andExpect(status().isCreated())
                .andReturn();

        return json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();
    }

    private long criarTarefa(String titulo, String data) throws Exception {
        var resultado = mockMvc.perform(autenticado(post("/api/v1/tarefas"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("titulo", titulo, "dataPlanejada", data))))
                .andExpect(status().isCreated())
                .andReturn();

        return json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();
    }

    private void marcar(long habitoId, String data) throws Exception {
        mockMvc.perform(autenticado(put("/api/v1/habitos/" + habitoId + "/registros/" + data))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FEITO\"}"))
                .andExpect(status().isOk());
    }

    private void concluir(long tarefaId) throws Exception {
        mockMvc.perform(autenticado(put("/api/v1/tarefas/" + tarefaId + "/conclusao")))
                .andExpect(status().isOk());
    }

    private ResultActions resumo() throws Exception {
        return mockMvc.perform(autenticado(get("/api/v1/resumo"))).andExpect(status().isOk());
    }
}
