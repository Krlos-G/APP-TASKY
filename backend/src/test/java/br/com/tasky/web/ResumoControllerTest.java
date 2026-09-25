package br.com.tasky.web;

import br.com.tasky.RelogioAjustavel;
import br.com.tasky.TestcontainersConfiguration;
import br.com.tasky.entity.Habito;
import br.com.tasky.entity.RegistroHabito;
import br.com.tasky.entity.Tarefa;
import br.com.tasky.entity.Usuario;
import br.com.tasky.entity.enums.DiaSemana;
import br.com.tasky.entity.enums.StatusRegistroHabito;
import br.com.tasky.entity.enums.StatusTarefa;
import br.com.tasky.entity.enums.TipoAgenda;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O painel do dia e da semana.
 *
 * O relogio e fixo numa quarta-feira: sem isso nao daria para testar a
 * fronteira da semana, que e onde mora o erro facil de cometer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ResumoControllerTest.RelogioDeTesteConfig.class})
class ResumoControllerTest {

    /** Quarta-feira, 23 de setembro de 2026, 12:00 em Sao Paulo. */
    private static final Instant AGORA = Instant.parse("2026-09-23T15:00:00Z");

    private static final LocalDate DOMINGO = LocalDate.parse("2026-09-20");
    private static final LocalDate SEGUNDA = LocalDate.parse("2026-09-21");
    private static final LocalDate TERCA = LocalDate.parse("2026-09-22");
    private static final LocalDate QUARTA = LocalDate.parse("2026-09-23");

    @TestConfiguration
    static class RelogioDeTesteConfig {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return new RelogioAjustavel(AGORA);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private HabitoRepository habitoRepository;
    @Autowired private RegistroHabitoRepository registroRepository;
    @Autowired private TarefaRepository tarefaRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private TokenAcessoService tokenAcessoService;
    @Autowired private Clock clock;

    private Usuario usuario;
    private String token;

    @BeforeEach
    void preparar() {
        ((RelogioAjustavel) clock).definir(AGORA);

        tarefaRepository.deleteAll();
        registroRepository.deleteAll();
        habitoRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuario = criarUsuario("carlos@tasky.app");
        token = tokenAcessoService.gerar(usuario).valor();
    }

    // -------------------------------------------------------------------- dia

    @Test
    @DisplayName("o dia traz os mesmos numeros da tela Hoje")
    void diaEspelhaOHoje() throws Exception {
        Habito ler = habitoDiario(usuario, "Ler");
        marcar(ler, QUARTA, StatusRegistroHabito.FEITO);
        habitoDiario(usuario, "Meditar");

        tarefa(usuario, "De hoje", QUARTA, StatusTarefa.A_FAZER, null);
        tarefa(usuario, "Feita hoje", QUARTA, StatusTarefa.FEITA, AGORA);
        tarefa(usuario, "Esquecida", SEGUNDA, StatusTarefa.A_FAZER, null);

        resumo()
                .andExpect(jsonPath("$.data").value("2026-09-23"))
                .andExpect(jsonPath("$.dia.habitosFeitos").value(1))
                .andExpect(jsonPath("$.dia.habitosDevidos").value(2))
                .andExpect(jsonPath("$.dia.tarefasFeitas").value(1))
                .andExpect(jsonPath("$.dia.tarefasDoDia").value(2))
                .andExpect(jsonPath("$.dia.atrasadas").value(1));
    }

    // ----------------------------------------------------------------- semana

    @Test
    @DisplayName("a semana vai de segunda ate hoje")
    void periodoDaSemana() throws Exception {
        resumo()
                .andExpect(jsonPath("$.semana.inicio").value("2026-09-21"))
                .andExpect(jsonPath("$.semana.fim").value("2026-09-23"));
    }

    @Test
    @DisplayName("a semana conta so os dias ja cobrados, e o domingo anterior fica fora")
    void semanaContaAteHoje() throws Exception {
        Habito ler = habitoDiario(usuario, "Ler");
        marcar(ler, DOMINGO, StatusRegistroHabito.FEITO);
        marcar(ler, SEGUNDA, StatusRegistroHabito.FEITO);
        marcar(ler, QUARTA, StatusRegistroHabito.FEITO);

        // Tres dias cobrados (seg, ter, qua), dois feitos. O domingo e de outra
        // semana e nao entra em nenhuma das duas contas.
        resumo()
                .andExpect(jsonPath("$.semana.habitosFeitos").value(2))
                .andExpect(jsonPath("$.semana.habitosCobrados").value(3));
    }

    @Test
    @DisplayName("pulado nao conta como feito nem como cobrado")
    void puladoForaDasDuasContas() throws Exception {
        Habito ler = habitoDiario(usuario, "Ler");
        marcar(ler, SEGUNDA, StatusRegistroHabito.FEITO);
        marcar(ler, TERCA, StatusRegistroHabito.PULADO);

        resumo()
                .andExpect(jsonPath("$.semana.habitosFeitos").value(1))
                .andExpect(jsonPath("$.semana.habitosCobrados").value(2));
    }

    @Test
    @DisplayName("marcar fora da agenda nao entra na conta da semana")
    void bonusForaDaSemana() throws Exception {
        Habito academia = habitoNosDias(usuario, "Academia", DiaSemana.TER, DiaSemana.QUI);
        marcar(academia, SEGUNDA, StatusRegistroHabito.FEITO);

        // Segunda nao e dia dele: so a terca foi cobrada, e ficou em branco.
        resumo()
                .andExpect(jsonPath("$.semana.habitosFeitos").value(0))
                .andExpect(jsonPath("$.semana.habitosCobrados").value(1));
    }

    @Test
    @DisplayName("habito arquivado sai das contas")
    void arquivadoForaDaSemana() throws Exception {
        Habito ler = habitoDiario(usuario, "Ler");
        marcar(ler, SEGUNDA, StatusRegistroHabito.FEITO);
        ler.setArquivadoEm(AGORA);
        habitoRepository.save(ler);

        resumo()
                .andExpect(jsonPath("$.semana.habitosCobrados").value(0))
                .andExpect(jsonPath("$.streaks.length()").value(0));
    }

    @Test
    @DisplayName("tarefa conta pelo momento em que foi concluida")
    void tarefasDaSemana() throws Exception {
        tarefa(usuario, "Feita na segunda", SEGUNDA, StatusTarefa.FEITA,
                Instant.parse("2026-09-21T13:00:00Z"));
        tarefa(usuario, "Feita no domingo", DOMINGO, StatusTarefa.FEITA,
                Instant.parse("2026-09-20T13:00:00Z"));
        tarefa(usuario, "Ainda a fazer", QUARTA, StatusTarefa.A_FAZER, null);

        resumo().andExpect(jsonPath("$.semana.tarefasConcluidas").value(1));
    }

    // ---------------------------------------------------------------- streaks

    @Test
    @DisplayName("as sequencias vem da maior para a menor")
    void streaksOrdenados() throws Exception {
        Habito ler = habitoDiario(usuario, "Ler");
        Habito agua = habitoDiario(usuario, "Beber agua");
        habitoDiario(usuario, "Meditar");

        marcar(ler, QUARTA, StatusRegistroHabito.FEITO);
        marcar(ler, TERCA, StatusRegistroHabito.FEITO);
        marcar(agua, QUARTA, StatusRegistroHabito.FEITO);

        resumo()
                .andExpect(jsonPath("$.streaks[0].nome").value("Ler"))
                .andExpect(jsonPath("$.streaks[0].streak").value(2))
                .andExpect(jsonPath("$.streaks[1].nome").value("Beber agua"))
                .andExpect(jsonPath("$.streaks[1].streak").value(1))
                .andExpect(jsonPath("$.streaks[2].nome").value("Meditar"))
                .andExpect(jsonPath("$.streaks[2].streak").value(0));
    }

    // ------------------------------------------------------------ propriedade

    @Test
    @DisplayName("o resumo nao enxerga os dados de outra conta")
    void naoVazaEntreContas() throws Exception {
        Usuario outro = criarUsuario("outro@tasky.app");
        Habito dele = habitoDiario(outro, "Ler");
        marcar(dele, QUARTA, StatusRegistroHabito.FEITO);
        tarefa(outro, "Dele", QUARTA, StatusTarefa.FEITA, AGORA);

        resumo()
                .andExpect(jsonPath("$.dia.habitosDevidos").value(0))
                .andExpect(jsonPath("$.dia.tarefasDoDia").value(0))
                .andExpect(jsonPath("$.semana.habitosCobrados").value(0))
                .andExpect(jsonPath("$.semana.tarefasConcluidas").value(0))
                .andExpect(jsonPath("$.streaks.length()").value(0));
    }

    // ------------------------------------------------------------------ apoio

    private ResultActions resumo() throws Exception {
        return mockMvc.perform(get("/api/v1/resumo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private Usuario criarUsuario(String email) {
        var novo = new Usuario();
        novo.setEmail(email);
        novo.setSenhaHash("nao-importa");
        novo.setNomeExibicao(email);
        novo.setFusoHorario("America/Sao_Paulo");
        return usuarioRepository.save(novo);
    }

    private Habito habitoDiario(Usuario dono, String nome) {
        var habito = new Habito();
        habito.setUsuario(dono);
        habito.setNome(nome);
        habito.setTipoAgenda(TipoAgenda.DIARIO);
        return habitoRepository.save(habito);
    }

    private Habito habitoNosDias(Usuario dono, String nome, DiaSemana... dias) {
        var habito = new Habito();
        habito.setUsuario(dono);
        habito.setNome(nome);
        habito.setTipoAgenda(TipoAgenda.DIAS_SEMANA);
        habito.setDiasSemana(EnumSet.copyOf(List.of(dias)));
        return habitoRepository.save(habito);
    }

    private void marcar(Habito habito, LocalDate data, StatusRegistroHabito status) {
        var registro = new RegistroHabito();
        registro.setHabito(habito);
        registro.setData(data);
        registro.setStatus(status);
        registroRepository.save(registro);
    }

    private void tarefa(Usuario dono, String titulo, LocalDate data, StatusTarefa status,
                        Instant concluidoEm) {
        var tarefa = new Tarefa();
        tarefa.setUsuario(dono);
        tarefa.setTitulo(titulo);
        tarefa.setDataPlanejada(data);
        tarefa.setStatus(status);
        tarefa.setConcluidoEm(concluidoEm);
        tarefaRepository.save(tarefa);
    }
}
