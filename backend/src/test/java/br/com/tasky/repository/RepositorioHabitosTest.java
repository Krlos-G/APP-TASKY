package br.com.tasky.repository;

import br.com.tasky.TestcontainersConfiguration;
import br.com.tasky.entity.Habito;
import br.com.tasky.entity.RegistroHabito;
import br.com.tasky.entity.Usuario;
import br.com.tasky.entity.enums.DiaSemana;
import br.com.tasky.entity.enums.StatusRegistroHabito;
import br.com.tasky.entity.enums.TipoAgenda;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * As consultas de habito, no banco de verdade.
 *
 * A fatia inteira se apoia em duas garantias que so o banco pode dar: nenhuma
 * leitura atravessa a fronteira entre contas, e nao existem duas marcacoes do
 * mesmo habito no mesmo dia. Testar isso na camada de cima seria testar a
 * consulta pelo reflexo dela.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RepositorioHabitosTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 7);

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private HabitoRepository habitoRepository;
    @Autowired private RegistroHabitoRepository registroRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate emTransacao;
    private Usuario dono;
    private Usuario outro;

    @BeforeEach
    void preparar() {
        emTransacao = new TransactionTemplate(transactionManager);

        registroRepository.deleteAll();
        habitoRepository.deleteAll();
        usuarioRepository.deleteAll();

        dono = criarUsuario("dono@tasky.app");
        outro = criarUsuario("outro@tasky.app");
    }

    // ------------------------------------------------------------ propriedade

    @Nested
    @DisplayName("isolamento entre contas")
    class Isolamento {

        @Test
        @DisplayName("a lista de ativos so traz os habitos do dono, sem os arquivados")
        void listaAtivos() {
            criarHabito(dono, "Ler");
            Habito antigo = criarHabito(dono, "Correr");
            arquivar(antigo);
            criarHabito(outro, "Meditar");

            assertThat(habitoRepository.findByUsuarioIdAndArquivadoEmIsNullOrderByNomeAsc(dono.getId()))
                    .extracting(Habito::getNome)
                    .containsExactly("Ler");

            // O arquivado nao sumiu: so saiu da lista do dia a dia.
            assertThat(habitoRepository.findByUsuarioIdOrderByNomeAsc(dono.getId()))
                    .extracting(Habito::getNome)
                    .containsExactly("Correr", "Ler");
        }

        @Test
        @DisplayName("buscar por id nao atravessa a fronteira da conta")
        void buscaPorId() {
            Habito alheio = criarHabito(outro, "Meditar");

            assertThat(habitoRepository.findByIdAndUsuarioId(alheio.getId(), dono.getId())).isEmpty();
            assertThat(habitoRepository.findByIdAndUsuarioId(alheio.getId(), outro.getId())).isPresent();
        }

        @Test
        @DisplayName("as marcacoes de uma conta nao aparecem na consulta da outra")
        void marcacoes() {
            Habito meu = criarHabito(dono, "Ler");
            Habito alheio = criarHabito(outro, "Meditar");
            marcar(meu, HOJE, StatusRegistroHabito.FEITO);
            marcar(alheio, HOJE, StatusRegistroHabito.FEITO);

            assertThat(registroRepository.marcacoesDoUsuario(dono.getId(), HOJE.minusDays(7), HOJE))
                    .singleElement()
                    .satisfies(m -> assertThat(m.habitoId()).isEqualTo(meu.getId()));

            assertThat(registroRepository.marcacoesDoHabito(
                    alheio.getId(), dono.getId(), HOJE.minusDays(7), HOJE)).isEmpty();
        }
    }

    // --------------------------------------------------------------- periodos

    @Test
    @DisplayName("a consulta respeita o periodo pedido")
    void periodo() {
        Habito habito = criarHabito(dono, "Ler");
        marcar(habito, HOJE, StatusRegistroHabito.FEITO);
        marcar(habito, HOJE.minusDays(1), StatusRegistroHabito.PULADO);
        marcar(habito, HOJE.minusDays(30), StatusRegistroHabito.FEITO);

        assertThat(registroRepository.marcacoesDoUsuario(dono.getId(), HOJE.minusDays(2), HOJE))
                .extracting(m -> m.data())
                .containsExactlyInAnyOrder(HOJE, HOJE.minusDays(1));
    }

    @Test
    @DisplayName("o historico de um habito vem do mais recente para o mais antigo")
    void historicoOrdenado() {
        Habito habito = criarHabito(dono, "Ler");
        marcar(habito, HOJE.minusDays(2), StatusRegistroHabito.FEITO);
        marcar(habito, HOJE, StatusRegistroHabito.FEITO);
        marcar(habito, HOJE.minusDays(1), StatusRegistroHabito.PULADO);

        assertThat(registroRepository.marcacoesDoHabito(
                habito.getId(), dono.getId(), HOJE.minusDays(30), HOJE))
                .extracting(m -> m.data())
                .containsExactly(HOJE, HOJE.minusDays(1), HOJE.minusDays(2));
    }

    // ------------------------------------------------------------- marcacoes

    @Test
    @DisplayName("o banco barra duas marcacoes do mesmo habito no mesmo dia")
    void unicoPorDia() {
        Habito habito = criarHabito(dono, "Ler");
        marcar(habito, HOJE, StatusRegistroHabito.FEITO);

        // E o unique que sustenta o upsert: sem ele, uma corrida entre dois
        // toques no botao deixaria duas linhas para o mesmo dia.
        assertThatThrownBy(() -> marcar(habito, HOJE, StatusRegistroHabito.PULADO))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("apagar diz se havia marcacao, e apagar de novo nao e erro")
    void apagarIdempotente() {
        Habito habito = criarHabito(dono, "Ler");
        marcar(habito, HOJE, StatusRegistroHabito.FEITO);

        int primeira = apagar(habito, HOJE);
        int segunda = apagar(habito, HOJE);

        assertThat(primeira).isEqualTo(1);
        assertThat(segunda).isZero();
        assertThat(registroRepository.findByHabitoIdAndData(habito.getId(), HOJE)).isEmpty();
    }

    @Test
    @DisplayName("apagar o habito leva o historico junto")
    void cascata() {
        Habito habito = criarHabito(dono, "Ler");
        marcar(habito, HOJE, StatusRegistroHabito.FEITO);

        habitoRepository.delete(habito);

        assertThat(registroRepository.count()).isZero();
    }

    // ------------------------------------------------------------------------

    private Usuario criarUsuario(String email) {
        var usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setSenhaHash("nao-importa");
        usuario.setNomeExibicao(email);
        usuario.setFusoHorario("America/Sao_Paulo");
        return usuarioRepository.save(usuario);
    }

    private Habito criarHabito(Usuario usuario, String nome) {
        var habito = new Habito();
        habito.setUsuario(usuario);
        habito.setNome(nome);
        habito.setTipoAgenda(TipoAgenda.DIARIO);
        habito.setDiasSemana(EnumSet.noneOf(DiaSemana.class));
        return habitoRepository.save(habito);
    }

    private void arquivar(Habito habito) {
        habito.setArquivadoEm(Instant.parse("2026-09-01T12:00:00Z"));
        habitoRepository.save(habito);
    }

    /** O delete em massa exige transacao propria; o retorno e o que se afere. */
    private int apagar(Habito habito, LocalDate data) {
        Integer linhas = emTransacao.execute(s -> registroRepository.apagar(habito.getId(), data));
        return linhas == null ? 0 : linhas;
    }

    private void marcar(Habito habito, LocalDate data, StatusRegistroHabito status) {
        var registro = new RegistroHabito();
        registro.setHabito(habito);
        registro.setData(data);
        registro.setStatus(status);
        registroRepository.saveAndFlush(registro);
    }
}
