package br.com.tasky.service;

import br.com.tasky.RelogioAjustavel;
import br.com.tasky.TestcontainersConfiguration;
import br.com.tasky.entity.AtribuicaoDia;
import br.com.tasky.entity.BlocoModelo;
import br.com.tasky.entity.Habito;
import br.com.tasky.entity.LembreteDia;
import br.com.tasky.entity.ModeloDia;
import br.com.tasky.entity.Tarefa;
import br.com.tasky.entity.Usuario;
import br.com.tasky.entity.enums.DiaSemana;
import br.com.tasky.entity.enums.StatusLembrete;
import br.com.tasky.entity.enums.StatusTarefa;
import br.com.tasky.entity.enums.TipoAgenda;
import br.com.tasky.entity.enums.TipoOrigemLembrete;
import br.com.tasky.repository.AtribuicaoDiaRepository;
import br.com.tasky.repository.HabitoRepository;
import br.com.tasky.repository.LembreteDiaRepository;
import br.com.tasky.repository.ModeloDiaRepository;
import br.com.tasky.repository.TarefaRepository;
import br.com.tasky.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A materializacao dos lembretes.
 *
 * Relogio fixo numa segunda-feira: a janela e "hoje e amanha", entao os testes
 * precisam saber exatamente que dias sao esses.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, LembreteServiceTest.RelogioDeTesteConfig.class})
class LembreteServiceTest {

    /** Segunda-feira, 28 de setembro de 2026, 08:00 em Sao Paulo. */
    private static final Instant AGORA = Instant.parse("2026-09-28T11:00:00Z");

    private static final LocalDate SEGUNDA = LocalDate.parse("2026-09-28");
    private static final LocalDate TERCA = LocalDate.parse("2026-09-29");
    private static final LocalDate QUARTA = LocalDate.parse("2026-09-30");

    @TestConfiguration
    static class RelogioDeTesteConfig {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return new RelogioAjustavel(AGORA);
        }
    }

    @Autowired private LembreteService lembreteService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private HabitoRepository habitoRepository;
    @Autowired private TarefaRepository tarefaRepository;
    @Autowired private ModeloDiaRepository modeloRepository;
    @Autowired private AtribuicaoDiaRepository atribuicaoRepository;
    @Autowired private LembreteDiaRepository lembreteRepository;
    @Autowired private Clock clock;

    private Usuario usuario;

    @BeforeEach
    void preparar() {
        ((RelogioAjustavel) clock).definir(AGORA);

        lembreteRepository.deleteAll();
        tarefaRepository.deleteAll();
        habitoRepository.deleteAll();
        atribuicaoRepository.deleteAll();
        modeloRepository.deleteAll();
        usuarioRepository.deleteAll();

        var novo = new Usuario();
        novo.setEmail("carlos@tasky.app");
        novo.setSenhaHash("nao-importa");
        novo.setNomeExibicao("Carlos");
        novo.setFusoHorario("America/Sao_Paulo");
        usuario = usuarioRepository.save(novo);
    }

    // ----------------------------------------------------------- cada origem

    @Test
    @DisplayName("bloco com antecedencia vira lembrete antes do inicio")
    void lembreteDeBloco() {
        rotinaNoDia(DiaSemana.SEG, "Foco", LocalTime.parse("09:00"), 15);

        lembreteService.materializar(usuario);

        assertThat(lembretes(TipoOrigemLembrete.BLOCO))
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.getTitulo()).isEqualTo("Foco");
                    assertThat(l.getCorpo()).isEqualTo("Comeca as 09:00");
                    // 08:45 em Sao Paulo = 11:45 UTC.
                    assertThat(l.getDispararEm()).isEqualTo(Instant.parse("2026-09-28T11:45:00Z"));
                    assertThat(l.getDataRef()).isEqualTo(SEGUNDA);
                    assertThat(l.getStatus()).isEqualTo(StatusLembrete.PENDENTE);
                });
    }

    @Test
    @DisplayName("bloco sem antecedencia nao vira lembrete")
    void blocoSemAntecedencia() {
        rotinaNoDia(DiaSemana.SEG, "Foco", LocalTime.parse("09:00"), null);

        lembreteService.materializar(usuario);

        assertThat(lembretes(TipoOrigemLembrete.BLOCO)).isEmpty();
    }

    @Test
    @DisplayName("habito devido com hora vira lembrete; nos dias que nao sao dele, nao")
    void lembreteDeHabito() {
        // Terca e quinta: na janela (segunda e terca) so a terca conta.
        habito("Academia", LocalTime.parse("19:00"), DiaSemana.TER, DiaSemana.QUI);

        lembreteService.materializar(usuario);

        assertThat(lembretes(TipoOrigemLembrete.HABITO))
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.getDataRef()).isEqualTo(TERCA);
                    assertThat(l.getDispararEm()).isEqualTo(Instant.parse("2026-09-29T22:00:00Z"));
                });
    }

    @Test
    @DisplayName("habito sem hora de lembrete nao vira lembrete")
    void habitoSemHora() {
        habito("Ler", null);

        lembreteService.materializar(usuario);

        assertThat(lembretes(TipoOrigemLembrete.HABITO)).isEmpty();
    }

    @Test
    @DisplayName("tarefa da janela com hora vira lembrete; a de depois de amanha nao")
    void lembreteDeTarefa() {
        tarefa("Responder e-mail", SEGUNDA, LocalTime.parse("14:30"));
        tarefa("Muito no futuro", QUARTA, LocalTime.parse("14:30"));

        lembreteService.materializar(usuario);

        assertThat(lembretes(TipoOrigemLembrete.TAREFA))
                .singleElement()
                .satisfies(l -> assertThat(l.getTitulo()).isEqualTo("Responder e-mail"));
    }

    @Test
    @DisplayName("o resumo diario vira um lembrete por dia da janela")
    void lembreteDeResumo() {
        usuario.setHoraResumoDiario(LocalTime.parse("07:00"));
        usuario = usuarioRepository.save(usuario);

        lembreteService.materializar(usuario);

        assertThat(lembretes(TipoOrigemLembrete.RESUMO))
                .hasSize(2)
                .extracting(LembreteDia::getDataRef)
                .containsExactlyInAnyOrder(SEGUNDA, TERCA);
    }

    // ---------------------------------------------------------- reconciliacao

    @Test
    @DisplayName("rodar duas vezes nao duplica nada")
    void idempotente() {
        habito("Ler", LocalTime.parse("19:00"));
        usuario.setHoraResumoDiario(LocalTime.parse("07:00"));
        usuario = usuarioRepository.save(usuario);

        lembreteService.materializar(usuario);
        long depoisDaPrimeira = lembreteRepository.count();
        lembreteService.materializar(usuario);

        assertThat(lembreteRepository.count()).isEqualTo(depoisDaPrimeira);
    }

    @Test
    @DisplayName("mudar a hora do habito corrige o lembrete pendente")
    void mudancaDeHoraCorrige() {
        Habito ler = habito("Ler", LocalTime.parse("19:00"));
        lembreteService.materializar(usuario);

        ler.setHoraLembrete(LocalTime.parse("21:30"));
        habitoRepository.save(ler);
        lembreteService.materializar(usuario);

        assertThat(lembretes(TipoOrigemLembrete.HABITO))
                .filteredOn(l -> l.getDataRef().equals(SEGUNDA))
                .singleElement()
                // 21:30 em Sao Paulo = 00:30 UTC do dia seguinte.
                .satisfies(l -> assertThat(l.getDispararEm())
                        .isEqualTo(Instant.parse("2026-09-29T00:30:00Z")));
    }

    @Test
    @DisplayName("apagar a hora do habito cancela o lembrete que sobrou")
    void regraQueSomeCancela() {
        Habito ler = habito("Ler", LocalTime.parse("19:00"));
        lembreteService.materializar(usuario);

        ler.setHoraLembrete(null);
        habitoRepository.save(ler);
        lembreteService.materializar(usuario);

        assertThat(lembretes(TipoOrigemLembrete.HABITO))
                .isNotEmpty()
                .allSatisfy(l -> assertThat(l.getStatus()).isEqualTo(StatusLembrete.CANCELADO));
    }

    @Test
    @DisplayName("arquivar o habito cancela o lembrete pendente")
    void arquivarCancela() {
        Habito ler = habito("Ler", LocalTime.parse("19:00"));
        lembreteService.materializar(usuario);

        ler.setArquivadoEm(AGORA);
        habitoRepository.save(ler);
        lembreteService.materializar(usuario);

        assertThat(lembretes(TipoOrigemLembrete.HABITO))
                .allSatisfy(l -> assertThat(l.getStatus()).isEqualTo(StatusLembrete.CANCELADO));
    }

    @Test
    @DisplayName("lembrete ja enviado nao e mexido nem recriado")
    void enviadoNaoSeMexe() {
        Habito ler = habito("Ler", LocalTime.parse("19:00"));
        lembreteService.materializar(usuario);

        LembreteDia deSegunda = lembretes(TipoOrigemLembrete.HABITO).stream()
                .filter(l -> l.getDataRef().equals(SEGUNDA))
                .findFirst()
                .orElseThrow();
        deSegunda.setStatus(StatusLembrete.ENVIADO);
        deSegunda.setEnviadoEm(AGORA);
        lembreteRepository.save(deSegunda);

        ler.setHoraLembrete(LocalTime.parse("21:30"));
        habitoRepository.save(ler);
        lembreteService.materializar(usuario);

        assertThat(lembreteRepository.findById(deSegunda.getId()).orElseThrow())
                .satisfies(l -> {
                    assertThat(l.getStatus()).isEqualTo(StatusLembrete.ENVIADO);
                    // O passado nao muda por edicao: o horario continua o antigo.
                    assertThat(l.getDispararEm()).isEqualTo(Instant.parse("2026-09-28T22:00:00Z"));
                });
    }

    @Test
    @DisplayName("trocar o fuso reposiciona os lembretes pendentes")
    void trocaDeFusoReposiciona() {
        habito("Ler", LocalTime.parse("19:00"));
        lembreteService.materializar(usuario);

        usuario.setFusoHorario("Asia/Tokyo");
        usuario = usuarioRepository.save(usuario);
        lembreteService.materializar(usuario);

        // 19:00 em Toquio, na segunda, e 10:00 UTC.
        assertThat(lembretes(TipoOrigemLembrete.HABITO))
                .filteredOn(l -> l.getDataRef().equals(SEGUNDA))
                .singleElement()
                .satisfies(l -> assertThat(l.getDispararEm())
                        .isEqualTo(Instant.parse("2026-09-28T10:00:00Z")));
    }

    // ------------------------------------------------------------------ apoio

    private List<LembreteDia> lembretes(TipoOrigemLembrete tipo) {
        return lembreteRepository.findAll().stream()
                .filter(l -> l.getTipoOrigem() == tipo)
                .toList();
    }

    private void rotinaNoDia(DiaSemana dia, String titulo, LocalTime inicio, Integer antecedencia) {
        var modelo = new ModeloDia();
        modelo.setUsuario(usuario);
        modelo.setNome("Dia util");

        var bloco = new BlocoModelo();
        bloco.setModeloDia(modelo);
        bloco.setTitulo(titulo);
        bloco.setHoraInicio(inicio);
        bloco.setHoraFim(inicio.plusHours(1));
        bloco.setMinutosAntecedenciaLembrete(antecedencia);
        modelo.getBlocos().add(bloco);

        var salvo = modeloRepository.save(modelo);

        var atribuicao = new AtribuicaoDia();
        atribuicao.setUsuario(usuario);
        atribuicao.setDiaSemana(dia);
        atribuicao.setModeloDia(salvo);
        atribuicaoRepository.save(atribuicao);
    }

    private Habito habito(String nome, LocalTime horaLembrete, DiaSemana... dias) {
        var habito = new Habito();
        habito.setUsuario(usuario);
        habito.setNome(nome);
        habito.setHoraLembrete(horaLembrete);

        if (dias.length == 0) {
            habito.setTipoAgenda(TipoAgenda.DIARIO);
        } else {
            habito.setTipoAgenda(TipoAgenda.DIAS_SEMANA);
            habito.setDiasSemana(EnumSet.copyOf(List.of(dias)));
        }
        return habitoRepository.save(habito);
    }

    private void tarefa(String titulo, LocalDate data, LocalTime horaLembrete) {
        var tarefa = new Tarefa();
        tarefa.setUsuario(usuario);
        tarefa.setTitulo(titulo);
        tarefa.setDataPlanejada(data);
        tarefa.setHoraLembrete(horaLembrete);
        tarefa.setStatus(StatusTarefa.A_FAZER);
        tarefaRepository.save(tarefa);
    }
}
