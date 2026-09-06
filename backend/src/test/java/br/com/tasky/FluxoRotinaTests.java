package br.com.tasky;

import br.com.tasky.entity.Usuario;
import br.com.tasky.repository.AtribuicaoDiaRepository;
import br.com.tasky.repository.ModeloDiaRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de fluxo da Fatia 3.
 *
 * Percorre a jornada real: criar o modelo, montar os blocos, atribuir a semana
 * e ver o dia se formar a partir disso. As etapas anteriores testam cada peca
 * isolada; aqui o que se verifica e que elas se encaixam na ordem em que o app
 * de verdade as usa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, FluxoRotinaTests.RelogioDeTesteConfig.class})
class FluxoRotinaTests {

    /** Quarta-feira, 2 de setembro de 2026, 10:30 em Sao Paulo. */
    private static final Instant INICIO = Instant.parse("2026-09-02T13:30:00Z");

    @TestConfiguration
    static class RelogioDeTesteConfig {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return new RelogioAjustavel(INICIO);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ModeloDiaRepository modeloRepository;
    @Autowired private AtribuicaoDiaRepository atribuicaoRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private TokenAcessoService tokenAcessoService;
    @Autowired private Clock clock;

    private String token;

    @BeforeEach
    void preparar() {
        ((RelogioAjustavel) clock).definir(INICIO);

        atribuicaoRepository.deleteAll();
        modeloRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        usuarioRepository.deleteAll();

        var usuario = new Usuario();
        usuario.setEmail("carlos@tasky.app");
        usuario.setSenhaHash("nao-importa");
        usuario.setNomeExibicao("Carlos");
        usuario.setFusoHorario("America/Sao_Paulo");
        token = tokenAcessoService.gerar(usuarioRepository.save(usuario)).valor();
    }

    private MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder req) {
        return req.header("Authorization", "Bearer " + token);
    }

    @Test
    @DisplayName("da rotina vazia ate a linha do tempo do dia")
    void jornadaCompleta() throws Exception {
        // -----------------------------------------------------------
        // 1. Comeco do zero: nenhuma rotina para hoje
        // -----------------------------------------------------------
        mockMvc.perform(autenticado(get("/api/v1/dia")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temRotina").value(false));

        // -----------------------------------------------------------
        // 2. Crio o modelo
        // -----------------------------------------------------------
        var criado = mockMvc.perform(autenticado(post("/api/v1/rotina/modelos"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nome", "Dia util", "padrao", true))))
                .andExpect(status().isCreated())
                .andReturn();
        long modeloId = json.readTree(criado.getResponse().getContentAsString()).get("id").asLong();

        // -----------------------------------------------------------
        // 3. Monto os blocos, de proposito fora de ordem
        // -----------------------------------------------------------
        adicionarBloco(modeloId, "Almoco", "12:00", "13:00");
        adicionarBloco(modeloId, "Treino", "07:00", "08:00");
        adicionarBloco(modeloId, "Foco", "09:00", "12:00");

        // -----------------------------------------------------------
        // 4. Atribuo a semana inteira de uma vez
        // -----------------------------------------------------------
        Map<String, Object> semana = new HashMap<>();
        for (String dia : new String[] {"SEG", "TER", "QUA", "QUI", "SEX"}) {
            semana.put(dia, modeloId);
        }
        mockMvc.perform(autenticado(put("/api/v1/rotina/semana"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("modeloPorDia", semana))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modeloPorDia.QUA.nome").value("Dia util"))
                .andExpect(jsonPath("$.modeloPorDia.SAB").doesNotExist());

        // -----------------------------------------------------------
        // 5. O dia agora se monta sozinho, com os blocos em ordem
        // -----------------------------------------------------------
        mockMvc.perform(autenticado(get("/api/v1/dia")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("2026-09-02"))
                .andExpect(jsonPath("$.diaSemana").value("QUA"))
                .andExpect(jsonPath("$.temRotina").value(true))
                .andExpect(jsonPath("$.nomeDoModelo").value("Dia util"))
                .andExpect(jsonPath("$.blocos[0].titulo").value("Treino"))
                .andExpect(jsonPath("$.blocos[1].titulo").value("Foco"))
                .andExpect(jsonPath("$.blocos[2].titulo").value("Almoco"));

        // -----------------------------------------------------------
        // 6. Sabado nao foi atribuido: continua sem rotina
        // -----------------------------------------------------------
        mockMvc.perform(autenticado(get("/api/v1/dia")).param("data", "2026-09-05"))
                .andExpect(jsonPath("$.diaSemana").value("SAB"))
                .andExpect(jsonPath("$.temRotina").value(false));

        // -----------------------------------------------------------
        // 7. O modelo em uso nao pode ser apagado sem aviso
        // -----------------------------------------------------------
        mockMvc.perform(autenticado(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/v1/rotina/modelos/" + modeloId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value(
                        org.hamcrest.Matchers.containsString("SEG")));
    }

    @Test
    @DisplayName("mudar a rotina muda o dia, sem tocar no historico do modelo")
    void mudarRotinaRefleteNoDia() throws Exception {
        long util = criarModelo("Dia util");
        adicionarBloco(util, "Foco", "09:00", "18:00");

        long folga = criarModelo("Folga");
        adicionarBloco(folga, "Preguica", "10:00", "12:00");

        atribuir("QUA", util);
        mockMvc.perform(autenticado(get("/api/v1/dia")))
                .andExpect(jsonPath("$.nomeDoModelo").value("Dia util"));

        // Trocar a atribuicao muda o dia imediatamente: o dia e derivado do
        // modelo, nunca uma copia dele.
        atribuir("QUA", folga);
        mockMvc.perform(autenticado(get("/api/v1/dia")))
                .andExpect(jsonPath("$.nomeDoModelo").value("Folga"))
                .andExpect(jsonPath("$.blocos[0].titulo").value("Preguica"));
    }

    // ------------------------------------------------------------------

    private long criarModelo(String nome) throws Exception {
        var resultado = mockMvc.perform(autenticado(post("/api/v1/rotina/modelos"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nome", nome, "padrao", false))))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();
    }

    private void adicionarBloco(long modeloId, String titulo, String inicio, String fim)
            throws Exception {
        mockMvc.perform(autenticado(post("/api/v1/rotina/modelos/" + modeloId + "/blocos"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "titulo", titulo, "horaInicio", inicio, "horaFim", fim))))
                .andExpect(status().isCreated());
    }

    private void atribuir(String dia, long modeloId) throws Exception {
        Map<String, Object> mapa = new HashMap<>();
        mapa.put(dia, modeloId);
        mockMvc.perform(autenticado(put("/api/v1/rotina/semana"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("modeloPorDia", mapa))))
                .andExpect(status().isOk());
    }
}
