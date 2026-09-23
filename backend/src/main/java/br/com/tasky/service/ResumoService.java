package br.com.tasky.service;

import br.com.tasky.entity.Usuario;
import br.com.tasky.entity.enums.StatusRegistroHabito;
import br.com.tasky.entity.enums.StatusTarefa;
import br.com.tasky.security.UsuarioAtual;
import br.com.tasky.web.dto.HabitoDoDiaResponse;
import br.com.tasky.web.dto.HabitoResponse;
import br.com.tasky.web.dto.ResumoResponse;
import br.com.tasky.web.dto.TarefaResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * O painel do dia e da semana.
 *
 * Nao calcula nada por conta propria: pergunta aos mesmos servicos que as telas
 * usam. E o que garante que o numero do Resumo e o numero do Hoje.
 */
@Service
public class ResumoService {

    private final HabitoService habitoService;
    private final TarefaService tarefaService;
    private final DataDoUsuario dataDoUsuario;
    private final UsuarioAtual usuarioAtual;

    public ResumoService(HabitoService habitoService, TarefaService tarefaService,
                         DataDoUsuario dataDoUsuario, UsuarioAtual usuarioAtual) {
        this.habitoService = habitoService;
        this.tarefaService = tarefaService;
        this.dataDoUsuario = dataDoUsuario;
        this.usuarioAtual = usuarioAtual;
    }

    @Transactional(readOnly = true)
    public ResumoResponse montar() {
        Usuario usuario = usuarioAtual.obrigatorio();
        LocalDate hoje = dataDoUsuario.hoje(usuario);
        LocalDate inicioDaSemana = hoje.with(DayOfWeek.MONDAY);

        return new ResumoResponse(
                hoje,
                doDia(usuario, hoje),
                daSemana(usuario, inicioDaSemana, hoje),
                sequencias());
    }

    private ResumoResponse.Dia doDia(Usuario usuario, LocalDate hoje) {
        List<HabitoDoDiaResponse> habitos = habitoService.doDia(usuario, hoje);
        List<TarefaResponse> tarefas = tarefaService.doDia(usuario, hoje);

        return new ResumoResponse.Dia(
                (int) habitos.stream()
                        .filter(h -> h.status() == StatusRegistroHabito.FEITO).count(),
                habitos.size(),
                (int) tarefas.stream()
                        .filter(t -> t.status() == StatusTarefa.FEITA).count(),
                tarefas.size(),
                tarefaService.atrasadas(usuario).size());
    }

    private ResumoResponse.Semana daSemana(Usuario usuario, LocalDate inicio, LocalDate fim) {
        HabitoService.Cumprimento habitos = habitoService.cumprimentoNoPeriodo(usuario, inicio, fim);

        return new ResumoResponse.Semana(
                inicio,
                fim,
                habitos.feitos(),
                habitos.cobrados(),
                tarefaService.concluidasNoPeriodo(usuario, inicio, fim));
    }

    private List<ResumoResponse.Sequencia> sequencias() {
        return habitoService.listar(false).stream()
                .sorted(Comparator.comparingInt(HabitoResponse::streak).reversed()
                        .thenComparing(HabitoResponse::nome))
                .map(h -> new ResumoResponse.Sequencia(h.id(), h.nome(), h.cor(), h.streak()))
                .toList();
    }
}
