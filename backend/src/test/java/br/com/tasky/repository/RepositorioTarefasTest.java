package br.com.tasky.repository;

import br.com.tasky.TestcontainersConfiguration;
import br.com.tasky.entity.Tarefa;
import br.com.tasky.entity.Usuario;
import br.com.tasky.entity.enums.StatusTarefa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;

import static br.com.tasky.entity.enums.StatusTarefa.A_FAZER;
import static br.com.tasky.entity.enums.StatusTarefa.CANCELADA;
import static br.com.tasky.entity.enums.StatusTarefa.FEITA;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RepositorioTarefasTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 16);
    private static final LocalDate ONTEM = HOJE.minusDays(1);
    private static final LocalDate AMANHA = HOJE.plusDays(1);

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TarefaRepository tarefaRepository;

    private Usuario dono;
    private Usuario outro;

    @BeforeEach
    void preparar() {
        tarefaRepository.deleteAll();
        usuarioRepository.deleteAll();

        dono = criarUsuario("dono@tasky.app");
        outro = criarUsuario("outro@tasky.app");
    }

    @Test
    @DisplayName("buscar por id nao atravessa a fronteira da conta")
    void buscaPorId() {
        Tarefa alheia = tarefa(outro, "Alheia", HOJE, A_FAZER);

        assertThat(tarefaRepository.findByIdAndUsuarioId(alheia.getId(), dono.getId())).isEmpty();
        assertThat(tarefaRepository.findByIdAndUsuarioId(alheia.getId(), outro.getId())).isPresent();
    }

    @Test
    @DisplayName("hoje traz as a fazer e as feitas do dia, e nada de outra conta")
    void hoje() {
        tarefa(dono, "Pendente", HOJE, A_FAZER);
        tarefa(dono, "Feita", HOJE, FEITA);
        tarefa(dono, "Cancelada", HOJE, CANCELADA);
        tarefa(dono, "De ontem", ONTEM, A_FAZER);
        tarefa(outro, "Alheia", HOJE, A_FAZER);

        assertThat(tarefaRepository.findByUsuarioIdAndDataPlanejadaAndStatusNot(
                dono.getId(), HOJE, CANCELADA))
                .extracting(Tarefa::getTitulo)
                .containsExactlyInAnyOrder("Pendente", "Feita");
    }

    @Test
    @DisplayName("atrasada e so a de dia passado ainda a fazer")
    void atrasadas() {
        tarefa(dono, "Esquecida", ONTEM, A_FAZER);
        tarefa(dono, "Feita ontem", ONTEM, FEITA);
        tarefa(dono, "De hoje", HOJE, A_FAZER);
        tarefa(outro, "Alheia", ONTEM, A_FAZER);

        assertThat(tarefaRepository.findByUsuarioIdAndStatusAndDataPlanejadaBefore(
                dono.getId(), A_FAZER, HOJE))
                .extracting(Tarefa::getTitulo)
                .containsExactly("Esquecida");
    }

    @Test
    @DisplayName("proximas comecam amanha")
    void proximas() {
        tarefa(dono, "Amanha", AMANHA, A_FAZER);
        tarefa(dono, "De hoje", HOJE, A_FAZER);
        tarefa(dono, "Feita adiantada", AMANHA, FEITA);

        assertThat(tarefaRepository.findByUsuarioIdAndStatusAndDataPlanejadaAfter(
                dono.getId(), A_FAZER, HOJE))
                .extracting(Tarefa::getTitulo)
                .containsExactly("Amanha");
    }

    @Test
    @DisplayName("sem data e a pendente que nao foi planejada para dia nenhum")
    void semData() {
        tarefa(dono, "Algum dia", null, A_FAZER);
        tarefa(dono, "Feita sem data", null, FEITA);
        tarefa(dono, "De hoje", HOJE, A_FAZER);

        assertThat(tarefaRepository.findByUsuarioIdAndStatusAndDataPlanejadaIsNull(
                dono.getId(), A_FAZER))
                .extracting(Tarefa::getTitulo)
                .containsExactly("Algum dia");
    }

    @Test
    @DisplayName("concluidas vem da mais recente para a mais antiga")
    void concluidas() {
        concluida("Primeira", "2026-09-14T12:00:00Z");
        concluida("Ultima", "2026-09-16T12:00:00Z");
        concluida("Do meio", "2026-09-15T12:00:00Z");
        tarefa(dono, "Pendente", HOJE, A_FAZER);

        assertThat(tarefaRepository.findByUsuarioIdAndStatusOrderByConcluidoEmDesc(
                dono.getId(), FEITA))
                .extracting(Tarefa::getTitulo)
                .containsExactly("Ultima", "Do meio", "Primeira");
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

    private Tarefa tarefa(Usuario usuario, String titulo, LocalDate data, StatusTarefa status) {
        var tarefa = new Tarefa();
        tarefa.setUsuario(usuario);
        tarefa.setTitulo(titulo);
        tarefa.setDataPlanejada(data);
        tarefa.setStatus(status);
        return tarefaRepository.saveAndFlush(tarefa);
    }

    private void concluida(String titulo, String concluidoEm) {
        var tarefa = new Tarefa();
        tarefa.setUsuario(dono);
        tarefa.setTitulo(titulo);
        tarefa.setStatus(FEITA);
        tarefa.setConcluidoEm(Instant.parse(concluidoEm));
        tarefaRepository.saveAndFlush(tarefa);
    }
}
