package br.com.tasky.web;

import br.com.tasky.TestcontainersConfiguration;
import br.com.tasky.entity.Usuario;
import br.com.tasky.repository.AtribuicaoDiaRepository;
import br.com.tasky.repository.ModeloDiaRepository;
import br.com.tasky.repository.RefreshTokenRepository;
import br.com.tasky.repository.UsuarioRepository;
import br.com.tasky.security.TokenAcessoService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RotinaControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ModeloDiaRepository modeloRepository;
    @Autowired private AtribuicaoDiaRepository atribuicaoRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private TokenAcessoService tokenAcessoService;

    private String tokenCarlos;
    private String tokenOutro;

    @BeforeEach
    void preparar() {
        atribuicaoRepository.deleteAll();
        modeloRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        usuarioRepository.deleteAll();

        tokenCarlos = tokenDe(criarUsuario("carlos@tasky.app", "Carlos"));
        tokenOutro = tokenDe(criarUsuario("outro@tasky.app", "Outro"));
    }

    private Usuario criarUsuario(String email, String nome) {
        var usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setSenhaHash("nao-importa");
        usuario.setNomeExibicao(nome);
        return usuarioRepository.save(usuario);
    }

    private String tokenDe(Usuario usuario) {
        return tokenAcessoService.gerar(usuario).valor();
    }

    private MockHttpServletRequestBuilder comAuth(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", "Bearer " + token);
    }

    private String corpo(Map<String, Object> campos) {
        return json.writeValueAsString(campos);
    }

    /** Cria um modelo e devolve o id. */
    private long criarModelo(String token, String nome) throws Exception {
        var resultado = mockMvc.perform(comAuth(post("/api/v1/rotina/modelos"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("nome", nome, "padrao", false))))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();
    }

    private void adicionarBloco(String token, long modeloId,
                                String titulo, String inicio, String fim) throws Exception {
        mockMvc.perform(comAuth(post("/api/v1/rotina/modelos/" + modeloId + "/blocos"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of(
                                "titulo", titulo, "horaInicio", inicio, "horaFim", fim))))
                .andExpect(status().isCreated());
    }

    @Nested
    @DisplayName("isolamento entre usuarios")
    class Isolamento {

        @Test
        @DisplayName("cada um so ve os proprios modelos")
        void listagemNaoVazaEntreUsuarios() throws Exception {
            criarModelo(tokenCarlos, "Dia util");
            criarModelo(tokenOutro, "Fim de semana");

            mockMvc.perform(comAuth(get("/api/v1/rotina/modelos"), tokenCarlos))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].nome").value("Dia util"));
        }

        @Test
        @DisplayName("modelo de outro dono responde 404, nao 403")
        void modeloAlheioDa404() throws Exception {
            long doOutro = criarModelo(tokenOutro, "Fim de semana");

            // 404 e nao 403 de proposito: 403 confirmaria que o recurso existe.
            mockMvc.perform(comAuth(get("/api/v1/rotina/modelos/" + doOutro), tokenCarlos))
                    .andExpect(status().isNotFound());

            mockMvc.perform(comAuth(put("/api/v1/rotina/modelos/" + doOutro), tokenCarlos)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of("nome", "Invadido", "padrao", false))))
                    .andExpect(status().isNotFound());

            mockMvc.perform(comAuth(delete("/api/v1/rotina/modelos/" + doOutro), tokenCarlos))
                    .andExpect(status().isNotFound());

            // E continua intacto.
            mockMvc.perform(comAuth(get("/api/v1/rotina/modelos/" + doOutro), tokenOutro))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nome").value("Fim de semana"));
        }

        @Test
        @DisplayName("nao da para pendurar bloco no modelo de outro")
        void blocoEmModeloAlheio() throws Exception {
            long doOutro = criarModelo(tokenOutro, "Fim de semana");

            mockMvc.perform(comAuth(post("/api/v1/rotina/modelos/" + doOutro + "/blocos"), tokenCarlos)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of(
                                    "titulo", "Invasao", "horaInicio", "09:00", "horaFim", "10:00"))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("sem token, nada da rotina responde")
        void semTokenNaoResponde() throws Exception {
            mockMvc.perform(get("/api/v1/rotina/modelos")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/rotina/semana")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("modelos e blocos")
    class ModelosEBlocos {

        @Test
        @DisplayName("os blocos voltam ordenados por horario")
        void blocosOrdenados() throws Exception {
            long id = criarModelo(tokenCarlos, "Dia util");
            adicionarBloco(tokenCarlos, id, "Almoco", "12:00", "13:00");
            adicionarBloco(tokenCarlos, id, "Treino", "07:00", "08:00");
            adicionarBloco(tokenCarlos, id, "Foco", "09:00", "12:00");

            mockMvc.perform(comAuth(get("/api/v1/rotina/modelos/" + id), tokenCarlos))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.blocos[0].titulo").value("Treino"))
                    .andExpect(jsonPath("$.blocos[1].titulo").value("Foco"))
                    .andExpect(jsonPath("$.blocos[2].titulo").value("Almoco"));
        }

        @Test
        @DisplayName("recusa bloco que termina antes de comecar")
        void horarioInvertido() throws Exception {
            long id = criarModelo(tokenCarlos, "Dia util");

            mockMvc.perform(comAuth(post("/api/v1/rotina/modelos/" + id + "/blocos"), tokenCarlos)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of(
                                    "titulo", "Invertido", "horaInicio", "18:00", "horaFim", "09:00"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("sobreposicao e permitida, mas volta como aviso")
        void sobreposicaoAvisa() throws Exception {
            long id = criarModelo(tokenCarlos, "Dia util");
            adicionarBloco(tokenCarlos, id, "Expediente", "09:00", "18:00");
            adicionarBloco(tokenCarlos, id, "Foco", "10:00", "12:00");

            mockMvc.perform(comAuth(get("/api/v1/rotina/modelos/" + id), tokenCarlos))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.blocos.length()").value(2))
                    .andExpect(jsonPath("$.sobreposicoes.length()").value(1))
                    .andExpect(jsonPath("$.sobreposicoes[0].descricao").value(
                            org.hamcrest.Matchers.containsString("se sobrepoem")));
        }

        @Test
        @DisplayName("blocos encostados nao contam como sobreposicao")
        void blocosAdjacentesNaoAvisam() throws Exception {
            long id = criarModelo(tokenCarlos, "Dia util");
            adicionarBloco(tokenCarlos, id, "Manha", "09:00", "12:00");
            adicionarBloco(tokenCarlos, id, "Tarde", "12:00", "18:00");

            mockMvc.perform(comAuth(get("/api/v1/rotina/modelos/" + id), tokenCarlos))
                    .andExpect(jsonPath("$.sobreposicoes.length()").value(0));
        }

        @Test
        @DisplayName("editar bloco muda o horario e devolve o modelo inteiro")
        void editarBloco() throws Exception {
            long id = criarModelo(tokenCarlos, "Dia util");
            adicionarBloco(tokenCarlos, id, "Treino", "09:00", "10:00");

            var modelo = mockMvc.perform(comAuth(get("/api/v1/rotina/modelos/" + id), tokenCarlos))
                    .andReturn();
            long blocoId = json.readTree(modelo.getResponse().getContentAsString())
                    .get("blocos").get(0).get("id").asLong();

            mockMvc.perform(comAuth(put("/api/v1/rotina/blocos/" + blocoId), tokenCarlos)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of(
                                    "titulo", "Treino", "horaInicio", "07:00", "horaFim", "08:00"))))
                    .andExpect(status().isOk())
                    // Devolve o modelo, nao o bloco: o front precisa dos avisos
                    // de sobreposicao recalculados.
                    .andExpect(jsonPath("$.blocos[0].horaInicio").value("07:00:00"))
                    .andExpect(jsonPath("$.sobreposicoes").isArray());
        }

        @Test
        @DisplayName("nao da para editar bloco de outro usuario")
        void editarBlocoAlheio() throws Exception {
            long doOutro = criarModelo(tokenOutro, "Fim de semana");
            adicionarBloco(tokenOutro, doOutro, "Preguica", "10:00", "12:00");

            var modelo = mockMvc.perform(comAuth(get("/api/v1/rotina/modelos/" + doOutro), tokenOutro))
                    .andReturn();
            long blocoId = json.readTree(modelo.getResponse().getContentAsString())
                    .get("blocos").get(0).get("id").asLong();

            mockMvc.perform(comAuth(put("/api/v1/rotina/blocos/" + blocoId), tokenCarlos)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of(
                                    "titulo", "Invadido", "horaInicio", "07:00", "horaFim", "08:00"))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("apagar bloco tira ele do modelo")
        void apagarBloco() throws Exception {
            long id = criarModelo(tokenCarlos, "Dia util");
            adicionarBloco(tokenCarlos, id, "Treino", "07:00", "08:00");

            var modelo = mockMvc.perform(comAuth(get("/api/v1/rotina/modelos/" + id), tokenCarlos))
                    .andReturn();
            long blocoId = json.readTree(modelo.getResponse().getContentAsString())
                    .get("blocos").get(0).get("id").asLong();

            mockMvc.perform(comAuth(delete("/api/v1/rotina/blocos/" + blocoId), tokenCarlos))
                    .andExpect(status().isNoContent());

            mockMvc.perform(comAuth(get("/api/v1/rotina/modelos/" + id), tokenCarlos))
                    .andExpect(jsonPath("$.blocos.length()").value(0));
        }
    }

    @Nested
    @DisplayName("semana")
    class Semana {

        @Test
        @DisplayName("a semana volta sempre com os sete dias")
        void semanaSempreCompleta() throws Exception {
            mockMvc.perform(comAuth(get("/api/v1/rotina/semana"), tokenCarlos))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.modeloPorDia.SEG").doesNotExist())
                    .andExpect(jsonPath("$.modeloPorDia.length()").value(7));
        }

        @Test
        @DisplayName("atribuir a semana substitui o conjunto inteiro")
        void atribuirSemanaSubstitui() throws Exception {
            long util = criarModelo(tokenCarlos, "Dia util");
            long fds = criarModelo(tokenCarlos, "Fim de semana");

            Map<String, Object> primeira = new HashMap<>();
            primeira.put("SEG", util);
            primeira.put("TER", util);
            enviarSemana(primeira);

            // Segunda chamada sem TER: ele precisa sumir, nao permanecer.
            Map<String, Object> segunda = new HashMap<>();
            segunda.put("SEG", util);
            segunda.put("SAB", fds);
            enviarSemana(segunda);

            mockMvc.perform(comAuth(get("/api/v1/rotina/semana"), tokenCarlos))
                    .andExpect(jsonPath("$.modeloPorDia.SEG.nome").value("Dia util"))
                    .andExpect(jsonPath("$.modeloPorDia.TER").doesNotExist())
                    .andExpect(jsonPath("$.modeloPorDia.SAB.nome").value("Fim de semana"));
        }

        @Test
        @DisplayName("nao da para atribuir modelo de outro usuario")
        void naoAtribuiModeloAlheio() throws Exception {
            long doOutro = criarModelo(tokenOutro, "Fim de semana");

            Map<String, Object> mapa = new HashMap<>();
            mapa.put("SEG", doOutro);

            mockMvc.perform(comAuth(put("/api/v1/rotina/semana"), tokenCarlos)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of("modeloPorDia", mapa))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("apagar modelo em uso e bloqueado com a lista de dias")
        void modeloEmUsoNaoApaga() throws Exception {
            long util = criarModelo(tokenCarlos, "Dia util");

            Map<String, Object> mapa = new HashMap<>();
            mapa.put("SEG", util);
            mapa.put("QUA", util);
            enviarSemana(mapa);

            var resposta = mockMvc.perform(comAuth(delete("/api/v1/rotina/modelos/" + util), tokenCarlos))
                    .andExpect(status().isConflict())
                    .andReturn();

            assertThat(resposta.getResponse().getContentAsString())
                    .contains("SEG").contains("QUA");
        }

        private void enviarSemana(Map<String, Object> mapa) throws Exception {
            mockMvc.perform(comAuth(put("/api/v1/rotina/semana"), tokenCarlos)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of("modeloPorDia", mapa))))
                    .andExpect(status().isOk());
        }
    }
}
