package br.com.tasky.web;

import br.com.tasky.RelogioAjustavel;
import br.com.tasky.TestcontainersConfiguration;
import br.com.tasky.entity.Usuario;
import br.com.tasky.repository.AtribuicaoDiaRepository;
import br.com.tasky.repository.HabitoRepository;
import br.com.tasky.repository.ModeloDiaRepository;
import br.com.tasky.repository.RefreshTokenRepository;
import br.com.tasky.repository.RegistroHabitoRepository;
import br.com.tasky.repository.UsuarioRepository;
import br.com.tasky.security.TokenAcessoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadastro de habitos pela API.
 *
 * O relogio e fixo porque a resposta carrega "devido hoje" e "status de hoje":
 * sem uma data conhecida, um habito de terca daria resultado diferente conforme
 * o dia em que a suite rodasse.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, HabitoControllerTest.RelogioDeTesteConfig.class})
class HabitoControllerTest {

    /** Segunda-feira, 7 de setembro de 2026, 12:00 em Sao Paulo. */
    private static final Instant AGORA = Instant.parse("2026-09-07T15:00:00Z");

    @TestConfiguration
    static class RelogioDeTesteConfig {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return new RelogioAjustavel(AGORA);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private HabitoRepository habitoRepository;
    @Autowired private RegistroHabitoRepository registroRepository;
    @Autowired private AtribuicaoDiaRepository atribuicaoRepository;
    @Autowired private ModeloDiaRepository modeloRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private TokenAcessoService tokenAcessoService;
    @Autowired private Clock clock;

    private String tokenCarlos;
    private String tokenOutro;

    @BeforeEach
    void preparar() {
        ((RelogioAjustavel) clock).definir(AGORA);

        registroRepository.deleteAll();
        habitoRepository.deleteAll();
        atribuicaoRepository.deleteAll();
        modeloRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        usuarioRepository.deleteAll();

        tokenCarlos = tokenDe(criarUsuario("carlos@tasky.app", "Carlos"));
        tokenOutro = tokenDe(criarUsuario("outro@tasky.app", "Outro"));
    }

    // ---------------------------------------------------------------- cadastro

    @Test
    @DisplayName("habito diario nasce sem streak e devido hoje")
    void criarDiario() throws Exception {
        mockMvc.perform(comAuth(post("/api/v1/habitos"), tokenCarlos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("nome", "Ler", "tipoAgenda", "DIARIO"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Ler"))
                .andExpect(jsonPath("$.streak").value(0))
                .andExpect(jsonPath("$.devidoHoje").value(true))
                // Nulo e diferente de PULADO: a tela precisa distinguir os dois.
                .andExpect(jsonPath("$.statusHoje").doesNotExist())
                .andExpect(jsonPath("$.arquivado").value(false));
    }

    @Test
    @DisplayName("habito de terca e quinta nao e cobrado na segunda")
    void criarPorDiasDaSemana() throws Exception {
        mockMvc.perform(comAuth(post("/api/v1/habitos"), tokenCarlos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of(
                                "nome", "Academia",
                                "tipoAgenda", "DIAS_SEMANA",
                                "diasSemana", List.of("TER", "QUI")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.devidoHoje").value(false))
                .andExpect(jsonPath("$.diasSemana.length()").value(2));
    }

    @Test
    @DisplayName("agenda por dias da semana sem nenhum dia e recusada")
    void agendaSemDias() throws Exception {
        Map<String, Object> pedido = new HashMap<>();
        pedido.put("nome", "Academia");
        pedido.put("tipoAgenda", "DIAS_SEMANA");
        pedido.put("diasSemana", List.of());

        mockMvc.perform(comAuth(post("/api/v1/habitos"), tokenCarlos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pedido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(
                        org.hamcrest.Matchers.containsString("ao menos um dia")));
    }

    @Test
    @DisplayName("agenda por vezes na semana ainda nao e aceita")
    void agendaForaDoMvp() throws Exception {
        mockMvc.perform(comAuth(post("/api/v1/habitos"), tokenCarlos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of(
                                "nome", "Correr",
                                "tipoAgenda", "VEZES_POR_SEMANA",
                                "metaSemanal", 3))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("nome em branco nao passa da validacao")
    void nomeObrigatorio() throws Exception {
        mockMvc.perform(comAuth(post("/api/v1/habitos"), tokenCarlos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("nome", "  ", "tipoAgenda", "DIARIO"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("nome"));
    }

    // ----------------------------------------------------------------- edicao

    @Test
    @DisplayName("editar troca nome e agenda de uma vez")
    void editar() throws Exception {
        long id = criarHabito(tokenCarlos, "Ler");

        mockMvc.perform(comAuth(put("/api/v1/habitos/" + id), tokenCarlos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of(
                                "nome", "Ler 20 paginas",
                                "tipoAgenda", "DIAS_SEMANA",
                                "diasSemana", List.of("SEG", "QUA", "SEX"),
                                "horaPreferida", "07:30:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ler 20 paginas"))
                .andExpect(jsonPath("$.tipoAgenda").value("DIAS_SEMANA"))
                .andExpect(jsonPath("$.horaPreferida").value("07:30:00"))
                .andExpect(jsonPath("$.devidoHoje").value(true));
    }

    @Test
    @DisplayName("voltar para diario limpa os dias da semana")
    void voltarParaDiario() throws Exception {
        long id = criarHabito(tokenCarlos, "Ler");

        mockMvc.perform(comAuth(put("/api/v1/habitos/" + id), tokenCarlos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of(
                                "nome", "Ler",
                                "tipoAgenda", "DIAS_SEMANA",
                                "diasSemana", List.of("TER")))))
                .andExpect(status().isOk());

        mockMvc.perform(comAuth(put("/api/v1/habitos/" + id), tokenCarlos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("nome", "Ler", "tipoAgenda", "DIARIO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasSemana.length()").value(0))
                .andExpect(jsonPath("$.devidoHoje").value(true));
    }

    // ------------------------------------------------------------- arquivamento

    @Test
    @DisplayName("arquivar tira da lista sem apagar, e desarquivar traz de volta")
    void arquivarEDesarquivar() throws Exception {
        long id = criarHabito(tokenCarlos, "Ler");

        mockMvc.perform(comAuth(put("/api/v1/habitos/" + id + "/arquivo"), tokenCarlos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("arquivado", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arquivado").value(true));

        mockMvc.perform(comAuth(get("/api/v1/habitos"), tokenCarlos))
                .andExpect(jsonPath("$.length()").value(0));

        // Continua existindo: arquivar preserva o historico, nao apaga.
        mockMvc.perform(comAuth(get("/api/v1/habitos"), tokenCarlos)
                        .param("incluirArquivados", "true"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].arquivado").value(true));

        mockMvc.perform(comAuth(put("/api/v1/habitos/" + id + "/arquivo"), tokenCarlos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("arquivado", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arquivado").value(false));

        mockMvc.perform(comAuth(get("/api/v1/habitos"), tokenCarlos))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("apagar remove o habito da lista")
    void apagar() throws Exception {
        long id = criarHabito(tokenCarlos, "Ler");

        mockMvc.perform(comAuth(delete("/api/v1/habitos/" + id), tokenCarlos))
                .andExpect(status().isNoContent());

        mockMvc.perform(comAuth(get("/api/v1/habitos"), tokenCarlos)
                        .param("incluirArquivados", "true"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("a lista vem ordenada por nome")
    void listaOrdenada() throws Exception {
        criarHabito(tokenCarlos, "Ler");
        criarHabito(tokenCarlos, "Academia");
        criarHabito(tokenCarlos, "Meditar");

        mockMvc.perform(comAuth(get("/api/v1/habitos"), tokenCarlos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Academia"))
                .andExpect(jsonPath("$[1].nome").value("Ler"))
                .andExpect(jsonPath("$[2].nome").value("Meditar"));
    }

    // ------------------------------------------------------------- propriedade

    @Nested
    @DisplayName("isolamento entre usuarios")
    class Isolamento {

        @Test
        @DisplayName("cada um so ve os proprios habitos")
        void listagemNaoVaza() throws Exception {
            criarHabito(tokenCarlos, "Ler");
            criarHabito(tokenOutro, "Meditar");

            mockMvc.perform(comAuth(get("/api/v1/habitos"), tokenCarlos))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].nome").value("Ler"));
        }

        @Test
        @DisplayName("habito de outro dono responde 404, nao 403")
        void habitoAlheioDa404() throws Exception {
            long doOutro = criarHabito(tokenOutro, "Meditar");

            // 404 e nao 403 de proposito: 403 confirmaria que o recurso existe.
            mockMvc.perform(comAuth(put("/api/v1/habitos/" + doOutro), tokenCarlos)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of("nome", "Invadido", "tipoAgenda", "DIARIO"))))
                    .andExpect(status().isNotFound());

            mockMvc.perform(comAuth(put("/api/v1/habitos/" + doOutro + "/arquivo"), tokenCarlos)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of("arquivado", true))))
                    .andExpect(status().isNotFound());

            mockMvc.perform(comAuth(delete("/api/v1/habitos/" + doOutro), tokenCarlos))
                    .andExpect(status().isNotFound());

            // E continua intacto para o dono.
            mockMvc.perform(comAuth(get("/api/v1/habitos"), tokenOutro))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].nome").value("Meditar"));
        }
    }

    // ------------------------------------------------------------------ apoio

    private Usuario criarUsuario(String email, String nome) {
        var usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setSenhaHash("nao-importa");
        usuario.setNomeExibicao(nome);
        usuario.setFusoHorario("America/Sao_Paulo");
        return usuarioRepository.save(usuario);
    }

    private String tokenDe(Usuario usuario) {
        return tokenAcessoService.gerar(usuario).valor();
    }

    private MockHttpServletRequestBuilder comAuth(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", "Bearer " + token);
    }

    private String corpo(Object valor) {
        return json.writeValueAsString(valor);
    }

    private long criarHabito(String token, String nome) throws Exception {
        var resultado = mockMvc.perform(comAuth(post("/api/v1/habitos"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("nome", nome, "tipoAgenda", "DIARIO"))))
                .andExpect(status().isCreated())
                .andReturn();

        return json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();
    }
}
