package br.com.tasky.web;

import br.com.tasky.RelogioAjustavel;
import br.com.tasky.TestcontainersConfiguration;
import br.com.tasky.entity.Usuario;
import br.com.tasky.repository.RefreshTokenRepository;
import br.com.tasky.repository.TarefaRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TarefaControllerTest.RelogioDeTesteConfig.class})
class TarefaControllerTest {

    /** Quarta-feira, 16 de setembro de 2026, 12:00 em Sao Paulo. */
    private static final Instant AGORA = Instant.parse("2026-09-16T15:00:00Z");

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
    @Autowired private TarefaRepository tarefaRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private TokenAcessoService tokenAcessoService;
    @Autowired private Clock clock;

    private Usuario carlos;
    private String tokenCarlos;
    private String tokenOutro;

    @BeforeEach
    void preparar() {
        ((RelogioAjustavel) clock).definir(AGORA);

        tarefaRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        usuarioRepository.deleteAll();

        carlos = criarUsuario("carlos@tasky.app");
        tokenCarlos = tokenDe(carlos);
        tokenOutro = tokenDe(criarUsuario("outro@tasky.app"));
    }

    // ---------------------------------------------------------------- cadastro

    @Test
    @DisplayName("tarefa nasce a fazer, com prioridade media por padrao")
    void criar() throws Exception {
        criar(tokenCarlos, Map.of("titulo", "Responder e-mail", "dataPlanejada", "2026-09-16"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Responder e-mail"))
                .andExpect(jsonPath("$.status").value("A_FAZER"))
                .andExpect(jsonPath("$.prioridade").value("MEDIA"))
                .andExpect(jsonPath("$.atrasada").value(false))
                .andExpect(jsonPath("$.vencida").value(false));
    }

    @Test
    @DisplayName("horario sem dia e recusado")
    void horarioSemData() throws Exception {
        criar(tokenCarlos, Map.of("titulo", "Reuniao", "horaPlanejada", "14:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(containsString("escolha tambem o dia")));
    }

    @Test
    @DisplayName("titulo em branco nao passa da validacao")
    void tituloObrigatorio() throws Exception {
        criar(tokenCarlos, Map.of("titulo", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("titulo"));
    }

    @Test
    @DisplayName("prioridade desconhecida e erro do cliente, nao do servidor")
    void prioridadeInvalida() throws Exception {
        criar(tokenCarlos, Map.of("titulo", "Ler", "prioridade", "URGENTE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("filtro desconhecido e erro do cliente, nao do servidor")
    void filtroInvalido() throws Exception {
        mockMvc.perform(comAuth(get("/api/v1/tarefas").param("filtro", "TODAS"), tokenCarlos))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("editar troca os campos e buscar devolve a versao nova")
    void editar() throws Exception {
        long id = idDe(criar(tokenCarlos, Map.of("titulo", "Ler")));

        Map<String, Object> pedido = new HashMap<>();
        pedido.put("titulo", "Ler 30 paginas");
        pedido.put("prioridade", "ALTA");
        pedido.put("minutosEstimados", 40);
        pedido.put("dataPlanejada", "2026-09-17");
        pedido.put("horaPlanejada", "19:00:00");

        mockMvc.perform(comAuth(put("/api/v1/tarefas/" + id), tokenCarlos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(pedido)))
                .andExpect(status().isOk());

        mockMvc.perform(comAuth(get("/api/v1/tarefas/" + id), tokenCarlos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Ler 30 paginas"))
                .andExpect(jsonPath("$.prioridade").value("ALTA"))
                .andExpect(jsonPath("$.minutosEstimados").value(40))
                .andExpect(jsonPath("$.horaPlanejada").value("19:00:00"));
    }

    @Test
    @DisplayName("apagar remove a tarefa")
    void apagar() throws Exception {
        long id = idDe(criar(tokenCarlos, Map.of("titulo", "Ler")));

        mockMvc.perform(comAuth(delete("/api/v1/tarefas/" + id), tokenCarlos))
                .andExpect(status().isNoContent());

        mockMvc.perform(comAuth(get("/api/v1/tarefas/" + id), tokenCarlos))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("tarefa de ontem nao feita e atrasada; prazo de ontem e vencido")
    void atrasadaEVencida() throws Exception {
        criar(tokenCarlos, Map.of(
                "titulo", "Esquecida",
                "dataPlanejada", "2026-09-15",
                "dataLimite", "2026-09-15"))
                .andExpect(jsonPath("$.atrasada").value(true))
                .andExpect(jsonPath("$.vencida").value(true));
    }

    // ----------------------------------------------------------------- filtros

    @Test
    @DisplayName("cada filtro traz so o que e dele")
    void filtros() throws Exception {
        criar(tokenCarlos, Map.of("titulo", "Hoje", "dataPlanejada", "2026-09-16"));
        criar(tokenCarlos, Map.of("titulo", "Amanha", "dataPlanejada", "2026-09-17"));
        criar(tokenCarlos, Map.of("titulo", "Ontem", "dataPlanejada", "2026-09-15"));
        criar(tokenCarlos, Map.of("titulo", "Algum dia"));
        long feita = idDe(criar(tokenCarlos, Map.of("titulo", "Feita", "dataPlanejada", "2026-09-10")));
        concluir(feita).andExpect(status().isOk());

        listar("HOJE").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].titulo").value("Hoje"));
        listar("PROXIMAS").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].titulo").value("Amanha"));
        listar("ATRASADAS").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].titulo").value("Ontem"));
        listar("SEM_DATA").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].titulo").value("Algum dia"));
        listar("CONCLUIDAS").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].titulo").value("Feita"));
    }

    @Test
    @DisplayName("sem filtro, a lista e a de hoje")
    void filtroPadrao() throws Exception {
        criar(tokenCarlos, Map.of("titulo", "Hoje", "dataPlanejada", "2026-09-16"));
        criar(tokenCarlos, Map.of("titulo", "Algum dia"));

        mockMvc.perform(comAuth(get("/api/v1/tarefas"), tokenCarlos))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].titulo").value("Hoje"));
    }

    @Test
    @DisplayName("o dia vem por horario, sem horario no fim, e alta prioridade primeiro no empate")
    void ordem() throws Exception {
        criar(tokenCarlos, Map.of("titulo", "Sem horario", "dataPlanejada", "2026-09-16"));
        criar(tokenCarlos, Map.of("titulo", "Tarde", "dataPlanejada", "2026-09-16",
                "horaPlanejada", "15:00:00"));
        criar(tokenCarlos, Map.of("titulo", "Manha baixa", "dataPlanejada", "2026-09-16",
                "horaPlanejada", "09:00:00", "prioridade", "BAIXA"));
        criar(tokenCarlos, Map.of("titulo", "Manha alta", "dataPlanejada", "2026-09-16",
                "horaPlanejada", "09:00:00", "prioridade", "ALTA"));

        listar("HOJE")
                .andExpect(jsonPath("$[0].titulo").value("Manha alta"))
                .andExpect(jsonPath("$[1].titulo").value("Manha baixa"))
                .andExpect(jsonPath("$[2].titulo").value("Tarde"))
                .andExpect(jsonPath("$[3].titulo").value("Sem horario"));
    }

    // --------------------------------------------------------------- conclusao

    @Test
    @DisplayName("concluir marca feita e guarda o momento; de novo nao muda nada")
    void concluirIdempotente() throws Exception {
        long id = idDe(criar(tokenCarlos, Map.of("titulo", "Ler", "dataPlanejada", "2026-09-16")));

        concluir(id)
                .andExpect(jsonPath("$.status").value("FEITA"))
                .andExpect(jsonPath("$.concluidoEm").value("2026-09-16T15:00:00Z"));

        // Avancar o relogio envelhece o token: reemitir, senao volta 401.
        ((RelogioAjustavel) clock).definir(AGORA.plusSeconds(3600));
        tokenCarlos = tokenDe(carlos);
        concluir(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concluidoEm").value("2026-09-16T15:00:00Z"));
    }

    @Test
    @DisplayName("desfazer volta a fazer e esquece o momento da conclusao")
    void desfazer() throws Exception {
        long id = idDe(criar(tokenCarlos, Map.of("titulo", "Ler", "dataPlanejada", "2026-09-16")));
        concluir(id).andExpect(status().isOk());

        mockMvc.perform(comAuth(delete("/api/v1/tarefas/" + id + "/conclusao"), tokenCarlos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("A_FAZER"))
                .andExpect(jsonPath("$.concluidoEm").doesNotExist());
    }

    @Test
    @DisplayName("tarefa feita nao conta como atrasada nem vencida")
    void feitaNaoAtrasa() throws Exception {
        long id = idDe(criar(tokenCarlos, Map.of(
                "titulo", "Ontem", "dataPlanejada", "2026-09-15", "dataLimite", "2026-09-15")));

        concluir(id)
                .andExpect(jsonPath("$.atrasada").value(false))
                .andExpect(jsonPath("$.vencida").value(false));
    }

    // ------------------------------------------------------------- propriedade

    @Nested
    @DisplayName("isolamento entre usuarios")
    class Isolamento {

        @Test
        @DisplayName("cada um so ve as proprias tarefas")
        void listagemNaoVaza() throws Exception {
            criar(tokenCarlos, Map.of("titulo", "Minha", "dataPlanejada", "2026-09-16"));
            criar(tokenOutro, Map.of("titulo", "Alheia", "dataPlanejada", "2026-09-16"));

            listar("HOJE")
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].titulo").value("Minha"));
        }

        @Test
        @DisplayName("tarefa de outro dono responde 404, nao 403")
        void tarefaAlheiaDa404() throws Exception {
            long alheia = idDe(criar(tokenOutro, Map.of("titulo", "Alheia")));

            mockMvc.perform(comAuth(get("/api/v1/tarefas/" + alheia), tokenCarlos))
                    .andExpect(status().isNotFound());

            mockMvc.perform(comAuth(put("/api/v1/tarefas/" + alheia), tokenCarlos)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("titulo", "Invadida"))))
                    .andExpect(status().isNotFound());

            concluir(alheia).andExpect(status().isNotFound());

            mockMvc.perform(comAuth(delete("/api/v1/tarefas/" + alheia), tokenCarlos))
                    .andExpect(status().isNotFound());

            mockMvc.perform(comAuth(get("/api/v1/tarefas/" + alheia), tokenOutro))
                    .andExpect(jsonPath("$.titulo").value("Alheia"));
        }
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

    private ResultActions criar(String token, Map<String, Object> pedido) throws Exception {
        return mockMvc.perform(comAuth(post("/api/v1/tarefas"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(pedido)));
    }

    private ResultActions listar(String filtro) throws Exception {
        return mockMvc.perform(comAuth(get("/api/v1/tarefas").param("filtro", filtro), tokenCarlos))
                .andExpect(status().isOk());
    }

    private ResultActions concluir(long id) throws Exception {
        return mockMvc.perform(comAuth(put("/api/v1/tarefas/" + id + "/conclusao"), tokenCarlos));
    }

    private long idDe(ResultActions resultado) throws Exception {
        String corpo = resultado.andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString();
        return json.readTree(corpo).get("id").asLong();
    }
}
