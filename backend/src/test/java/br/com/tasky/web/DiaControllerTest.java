package br.com.tasky.web;

import br.com.tasky.RelogioAjustavel;
import br.com.tasky.TestcontainersConfiguration;
import br.com.tasky.entity.AtribuicaoDia;
import br.com.tasky.entity.BlocoModelo;
import br.com.tasky.entity.ModeloDia;
import br.com.tasky.entity.Usuario;
import br.com.tasky.entity.enums.DiaSemana;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes da montagem do dia.
 *
 * O foco esta no fuso horario: "hoje" precisa ser o dia do usuario, nao o do
 * servidor. Sem relogio controlado nao daria para testar a virada da meia-noite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, DiaControllerTest.RelogioDeTesteConfig.class})
class DiaControllerTest {

    /** Quarta-feira, 2 de setembro de 2026, 12:00 UTC. */
    private static final Instant INICIO = Instant.parse("2026-09-02T12:00:00Z");

    @TestConfiguration
    static class RelogioDeTesteConfig {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return new RelogioAjustavel(INICIO);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ModeloDiaRepository modeloRepository;
    @Autowired private AtribuicaoDiaRepository atribuicaoRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private TokenAcessoService tokenAcessoService;
    @Autowired private Clock clock;

    private RelogioAjustavel relogio;
    private Usuario usuario;
    private String token;

    @BeforeEach
    void preparar() {
        relogio = (RelogioAjustavel) clock;
        relogio.definir(INICIO);

        atribuicaoRepository.deleteAll();
        modeloRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuario = novoUsuario("America/Sao_Paulo");
        token = tokenAcessoService.gerar(usuario).valor();
    }

    private Usuario novoUsuario(String fuso) {
        var novo = new Usuario();
        novo.setEmail("carlos@tasky.app");
        novo.setSenhaHash("nao-importa");
        novo.setNomeExibicao("Carlos");
        novo.setFusoHorario(fuso);
        return usuarioRepository.save(novo);
    }

    /** Cria um modelo com um bloco e o atribui ao dia informado. */
    private void rotinaNoDia(DiaSemana dia, String nomeModelo, String titulo,
                             String inicio, String fim) {
        var modelo = new ModeloDia();
        modelo.setUsuario(usuario);
        modelo.setNome(nomeModelo);

        var bloco = new BlocoModelo();
        bloco.setModeloDia(modelo);
        bloco.setTitulo(titulo);
        bloco.setHoraInicio(LocalTime.parse(inicio));
        bloco.setHoraFim(LocalTime.parse(fim));
        modelo.getBlocos().add(bloco);

        var salvo = modeloRepository.save(modelo);

        var atribuicao = new AtribuicaoDia();
        atribuicao.setUsuario(usuario);
        atribuicao.setDiaSemana(dia);
        atribuicao.setModeloDia(salvo);
        atribuicaoRepository.save(atribuicao);
    }

    /**
     * Avanca o relogio e reemite o token.
     *
     * O Clock e o mesmo para toda a aplicacao, entao viajar no tempo tambem
     * envelhece o access token: sem reemitir, a requisicao voltaria 401 e o
     * teste falharia por um motivo que nao e o que ele investiga.
     */
    private void agoraE(String instante) {
        relogio.definir(Instant.parse(instante));
        token = tokenAcessoService.gerar(usuario).valor();
    }

    private org.springframework.test.web.servlet.ResultActions buscarDia(String data) throws Exception {
        var req = data == null ? get("/api/v1/dia") : get("/api/v1/dia").param("data", data);
        return mockMvc.perform(req.header("Authorization", "Bearer " + token));
    }

    @Nested
    @DisplayName("sem rotina")
    class SemRotina {

        @Test
        @DisplayName("dia sem modelo atribuido volta 200, nao erro")
        void diaVazioNaoEErro() throws Exception {
            buscarDia(null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.temRotina").value(false))
                    .andExpect(jsonPath("$.nomeDoModelo").doesNotExist())
                    .andExpect(jsonPath("$.blocos.length()").value(0));
        }

        @Test
        @DisplayName("o contrato ja traz habitos e tarefas, mesmo vazios")
        void contratoReservaEspacoParaAsProximasFatias() throws Exception {
            buscarDia(null)
                    .andExpect(jsonPath("$.habitos").isArray())
                    .andExpect(jsonPath("$.tarefas").isArray());
        }
    }

    @Nested
    @DisplayName("com rotina")
    class ComRotina {

        @Test
        @DisplayName("devolve os blocos do modelo do dia")
        void devolveBlocosDoDia() throws Exception {
            // 2026-09-02 e uma quarta-feira.
            rotinaNoDia(DiaSemana.QUA, "Dia util", "Foco", "09:00", "12:00");

            buscarDia(null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("2026-09-02"))
                    .andExpect(jsonPath("$.diaSemana").value("QUA"))
                    .andExpect(jsonPath("$.temRotina").value(true))
                    .andExpect(jsonPath("$.nomeDoModelo").value("Dia util"))
                    .andExpect(jsonPath("$.blocos[0].titulo").value("Foco"));
        }

        @Test
        @DisplayName("a rotina de outro dia da semana nao aparece")
        void naoMisturaDiasDaSemana() throws Exception {
            rotinaNoDia(DiaSemana.SAB, "Fim de semana", "Preguica", "10:00", "12:00");

            // Hoje e quarta: o modelo de sabado nao pode vazar para ca.
            buscarDia(null).andExpect(jsonPath("$.temRotina").value(false));
        }

        @Test
        @DisplayName("data explicita busca o dia pedido, nao hoje")
        void dataExplicita() throws Exception {
            rotinaNoDia(DiaSemana.SAB, "Fim de semana", "Preguica", "10:00", "12:00");

            // 2026-09-05 e um sabado.
            buscarDia("2026-09-05")
                    .andExpect(jsonPath("$.diaSemana").value("SAB"))
                    .andExpect(jsonPath("$.temRotina").value(true))
                    .andExpect(jsonPath("$.blocos[0].titulo").value("Preguica"));
        }

        @Test
        @DisplayName("os blocos voltam ordenados por horario")
        void blocosOrdenados() throws Exception {
            var modelo = new ModeloDia();
            modelo.setUsuario(usuario);
            modelo.setNome("Dia util");
            for (String[] b : new String[][] {
                    {"Almoco", "12:00", "13:00"},
                    {"Treino", "07:00", "08:00"},
                    {"Foco", "09:00", "12:00"}}) {
                var bloco = new BlocoModelo();
                bloco.setModeloDia(modelo);
                bloco.setTitulo(b[0]);
                bloco.setHoraInicio(LocalTime.parse(b[1]));
                bloco.setHoraFim(LocalTime.parse(b[2]));
                modelo.getBlocos().add(bloco);
            }
            var salvo = modeloRepository.save(modelo);

            var atribuicao = new AtribuicaoDia();
            atribuicao.setUsuario(usuario);
            atribuicao.setDiaSemana(DiaSemana.QUA);
            atribuicao.setModeloDia(salvo);
            atribuicaoRepository.save(atribuicao);

            buscarDia(null)
                    .andExpect(jsonPath("$.blocos[0].titulo").value("Treino"))
                    .andExpect(jsonPath("$.blocos[1].titulo").value("Foco"))
                    .andExpect(jsonPath("$.blocos[2].titulo").value("Almoco"));
        }
    }

    @Nested
    @DisplayName("fuso horario")
    class Fuso {

        @Test
        @DisplayName("hoje segue o fuso do usuario, nao o do servidor")
        void hojeUsaFusoDoUsuario() throws Exception {
            // 2026-09-03T02:00Z ainda e dia 2 em Sao Paulo (UTC-3).
            agoraE("2026-09-03T02:00:00Z");

            buscarDia(null)
                    .andExpect(jsonPath("$.data").value("2026-09-02"))
                    .andExpect(jsonPath("$.diaSemana").value("QUA"));
        }

        @Test
        @DisplayName("passada a meia-noite local, o dia vira")
        void viradaDeDiaLocal() throws Exception {
            // 2026-09-03T03:00Z ja e dia 3 em Sao Paulo.
            agoraE("2026-09-03T03:00:00Z");

            buscarDia(null)
                    .andExpect(jsonPath("$.data").value("2026-09-03"))
                    .andExpect(jsonPath("$.diaSemana").value("QUI"));
        }

        @Test
        @DisplayName("dois fusos, no mesmo instante, veem dias diferentes")
        void fusosDiferentesVeemDiasDiferentes() throws Exception {
            agoraE("2026-09-03T02:00:00Z");

            // Sao Paulo (UTC-3) ainda esta no dia 2...
            buscarDia(null).andExpect(jsonPath("$.data").value("2026-09-02"));

            // ...e Toquio (UTC+9) ja esta no dia 3.
            usuario.setFusoHorario("Asia/Tokyo");
            usuarioRepository.save(usuario);

            buscarDia(null).andExpect(jsonPath("$.data").value("2026-09-03"));
        }
    }

    @Test
    @DisplayName("sem token nao responde")
    void exigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/v1/dia")).andExpect(status().isUnauthorized());
    }
}
