package br.com.tasky;

import br.com.tasky.entity.Usuario;
import br.com.tasky.repository.RefreshTokenRepository;
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
 * Teste de fluxo da Fatia 5.
 *
 * O relogio anda de um dia para o outro, que e a unica forma de provar o que
 * so o tempo revela: tarefa nao feita virando atrasada e prazo vencendo
 * sozinho, sem ninguem mexer no registro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, FluxoTarefasTests.RelogioDeTesteConfig.class})
class FluxoTarefasTests {

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
    @DisplayName("crio a tarefa, ela aparece no dia e eu concluo")
    void jornadaDaTarefa() throws Exception {
        long id = criar(Map.of(
                "titulo", "Responder e-mail",
                "dataPlanejada", "2026-09-21",
                "horaPlanejada", "14:30:00",
                "minutosEstimados", 30,
                "prioridade", "ALTA"));

        // -----------------------------------------------------------
        // 1. Ela entra no dia, com horario, e nada esta atrasado
        // -----------------------------------------------------------
        buscarDia(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tarefas.length()").value(1))
                .andExpect(jsonPath("$.tarefas[0].titulo").value("Responder e-mail"))
                .andExpect(jsonPath("$.tarefas[0].horaPlanejada").value("14:30:00"))
                .andExpect(jsonPath("$.tarefas[0].status").value("A_FAZER"))
                .andExpect(jsonPath("$.atrasadas.length()").value(0));

        // -----------------------------------------------------------
        // 2. Concluida, continua no dia - marcada
        // -----------------------------------------------------------
        concluir(id).andExpect(jsonPath("$.status").value("FEITA"));

        buscarDia(null)
                .andExpect(jsonPath("$.tarefas.length()").value(1))
                .andExpect(jsonPath("$.tarefas[0].status").value("FEITA"))
                .andExpect(jsonPath("$.atrasadas.length()").value(0));

        // -----------------------------------------------------------
        // 3. No dia seguinte ela sai de cena: feita nao cobra nada
        // -----------------------------------------------------------
        agoraE("2026-09-22T15:00:00Z");

        buscarDia(null)
                .andExpect(jsonPath("$.tarefas.length()").value(0))
                .andExpect(jsonPath("$.atrasadas.length()").value(0));

        listar("CONCLUIDAS")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].titulo").value("Responder e-mail"));
    }

    @Test
    @DisplayName("a tarefa que eu nao fiz vira atrasada sozinha na virada do dia")
    void naoFeitaViraAtrasada() throws Exception {
        criar(Map.of("titulo", "Pagar boleto", "dataPlanejada", "2026-09-21"));

        buscarDia(null).andExpect(jsonPath("$.tarefas.length()").value(1));

        // Ninguem mexeu no registro: so o dia mudou.
        agoraE("2026-09-22T15:00:00Z");

        buscarDia(null)
                .andExpect(jsonPath("$.tarefas.length()").value(0))
                .andExpect(jsonPath("$.atrasadas.length()").value(1))
                .andExpect(jsonPath("$.atrasadas[0].titulo").value("Pagar boleto"))
                .andExpect(jsonPath("$.atrasadas[0].atrasada").value(true));

        listar("ATRASADAS").andExpect(jsonPath("$.length()").value(1));
        listar("HOJE").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("o prazo vence sozinho, e concluir tira o aviso")
    void prazoVence() throws Exception {
        long id = criar(Map.of("titulo", "Entregar relatorio", "dataLimite", "2026-09-21"));

        // Sem dia planejado, ela nao aparece no dia - mora no backlog.
        buscarDia(null).andExpect(jsonPath("$.tarefas.length()").value(0));
        listar("SEM_DATA")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].vencida").value(false));

        agoraE("2026-09-22T15:00:00Z");
        listar("SEM_DATA").andExpect(jsonPath("$[0].vencida").value(true));

        // Feita nao cobra mais nada, mesmo com o prazo no passado.
        concluir(id).andExpect(jsonPath("$.vencida").value(false));
    }

    @Test
    @DisplayName("olhando o dia seguinte, atrasadas nao vem")
    void atrasadasSoNoDiaDeHoje() throws Exception {
        criar(Map.of("titulo", "Pagar boleto", "dataPlanejada", "2026-09-20"));

        buscarDia(null).andExpect(jsonPath("$.atrasadas.length()").value(1));

        // Em outro dia a pergunta "atrasada em relacao a que" nao tem resposta.
        buscarDia("2026-09-22").andExpect(jsonPath("$.atrasadas.length()").value(0));
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

    private long criar(Map<String, Object> pedido) throws Exception {
        var resultado = mockMvc.perform(autenticado(post("/api/v1/tarefas"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(pedido)))
                .andExpect(status().isCreated())
                .andReturn();

        return json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions concluir(long id) throws Exception {
        return mockMvc.perform(autenticado(put("/api/v1/tarefas/" + id + "/conclusao")))
                .andExpect(status().isOk());
    }

    private ResultActions buscarDia(String data) throws Exception {
        var req = data == null ? get("/api/v1/dia") : get("/api/v1/dia").param("data", data);
        return mockMvc.perform(autenticado(req)).andExpect(status().isOk());
    }

    private ResultActions listar(String filtro) throws Exception {
        return mockMvc.perform(autenticado(get("/api/v1/tarefas").param("filtro", filtro)))
                .andExpect(status().isOk());
    }
}
