package br.com.tasky.service;

import br.com.tasky.entity.Tarefa;
import br.com.tasky.entity.Usuario;
import br.com.tasky.entity.enums.Prioridade;
import br.com.tasky.repository.TarefaRepository;
import br.com.tasky.security.UsuarioAtual;
import br.com.tasky.web.ApiException;
import br.com.tasky.web.dto.TarefaRequest;
import br.com.tasky.web.dto.TarefaResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static br.com.tasky.entity.enums.StatusTarefa.A_FAZER;
import static br.com.tasky.entity.enums.StatusTarefa.CANCELADA;
import static br.com.tasky.entity.enums.StatusTarefa.FEITA;

@Service
public class TarefaService {

    /** Ordenado em memoria: no SQL, o enum de prioridade sairia em ordem alfabetica. */
    private static final Comparator<Tarefa> ORDEM_DE_EXECUCAO = Comparator
            .comparing(Tarefa::getDataPlanejada, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Tarefa::getHoraPlanejada, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Tarefa::getPrioridade, Comparator.reverseOrder())
            .thenComparing(Tarefa::getTitulo);

    private final TarefaRepository tarefaRepository;
    private final DataDoUsuario dataDoUsuario;
    private final UsuarioAtual usuarioAtual;
    private final Clock clock;

    public TarefaService(TarefaRepository tarefaRepository, DataDoUsuario dataDoUsuario,
                         UsuarioAtual usuarioAtual, Clock clock) {
        this.tarefaRepository = tarefaRepository;
        this.dataDoUsuario = dataDoUsuario;
        this.usuarioAtual = usuarioAtual;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TarefaResponse> listar(FiltroTarefa filtro) {
        Usuario usuario = usuarioAtual.obrigatorio();
        LocalDate hoje = dataDoUsuario.hoje(usuario);
        Long id = usuario.getId();

        return switch (filtro) {
            case HOJE -> doDia(usuario, hoje);
            case ATRASADAS -> atrasadas(usuario);
            case PROXIMAS -> responder(ordenar(
                    tarefaRepository.findByUsuarioIdAndStatusAndDataPlanejadaAfter(id, A_FAZER, hoje)), hoje);
            case SEM_DATA -> responder(ordenar(
                    tarefaRepository.findByUsuarioIdAndStatusAndDataPlanejadaIsNull(id, A_FAZER)), hoje);
            case CONCLUIDAS -> responder(
                    tarefaRepository.findByUsuarioIdAndStatusOrderByConcluidoEmDesc(id, FEITA), hoje);
        };
    }

    /** As a fazer e as feitas daquela data: feita continua na tela, marcada. */
    @Transactional(readOnly = true)
    public List<TarefaResponse> doDia(Usuario usuario, LocalDate data) {
        return responder(ordenar(tarefaRepository.findByUsuarioIdAndDataPlanejadaAndStatusNot(
                usuario.getId(), data, CANCELADA)), dataDoUsuario.hoje(usuario));
    }

    @Transactional(readOnly = true)
    public List<TarefaResponse> atrasadas(Usuario usuario) {
        LocalDate hoje = dataDoUsuario.hoje(usuario);
        return responder(ordenar(tarefaRepository.findByUsuarioIdAndStatusAndDataPlanejadaBefore(
                usuario.getId(), A_FAZER, hoje)), hoje);
    }

    @Transactional(readOnly = true)
    public TarefaResponse buscar(Long id) {
        Usuario usuario = usuarioAtual.obrigatorio();
        return TarefaResponse.de(daConta(id, usuario), dataDoUsuario.hoje(usuario));
    }

    @Transactional
    public TarefaResponse criar(TarefaRequest pedido) {
        Usuario usuario = usuarioAtual.obrigatorio();

        var tarefa = new Tarefa();
        tarefa.setUsuario(usuario);
        aplicar(pedido, tarefa);

        return TarefaResponse.de(tarefaRepository.save(tarefa), dataDoUsuario.hoje(usuario));
    }

    @Transactional
    public TarefaResponse atualizar(Long id, TarefaRequest pedido) {
        Usuario usuario = usuarioAtual.obrigatorio();
        Tarefa tarefa = daConta(id, usuario);
        aplicar(pedido, tarefa);

        return TarefaResponse.de(tarefaRepository.save(tarefa), dataDoUsuario.hoje(usuario));
    }

    @Transactional
    public void apagar(Long id) {
        Usuario usuario = usuarioAtual.obrigatorio();
        tarefaRepository.delete(daConta(id, usuario));
    }

    /** Idempotente: concluir de novo nao sobrescreve o momento da primeira conclusao. */
    @Transactional
    public TarefaResponse concluir(Long id) {
        Usuario usuario = usuarioAtual.obrigatorio();
        Tarefa tarefa = daConta(id, usuario);

        if (tarefa.getStatus() != FEITA) {
            tarefa.setStatus(FEITA);
            tarefa.setConcluidoEm(clock.instant());
        }

        return TarefaResponse.de(tarefa, dataDoUsuario.hoje(usuario));
    }

    @Transactional
    public TarefaResponse desfazerConclusao(Long id) {
        Usuario usuario = usuarioAtual.obrigatorio();
        Tarefa tarefa = daConta(id, usuario);

        tarefa.setStatus(A_FAZER);
        tarefa.setConcluidoEm(null);

        return TarefaResponse.de(tarefa, dataDoUsuario.hoje(usuario));
    }

    // ------------------------------------------------------------------ apoio

    private Tarefa daConta(Long id, Usuario usuario) {
        return tarefaRepository.findByIdAndUsuarioId(id, usuario.getId())
                .orElseThrow(() -> ApiException.naoEncontrado("Tarefa"));
    }

    private void aplicar(TarefaRequest pedido, Tarefa tarefa) {
        if (pedido.horaPlanejada() != null && pedido.dataPlanejada() == null) {
            throw ApiException.horarioSemData();
        }

        tarefa.setTitulo(pedido.titulo().trim());
        tarefa.setObservacoes(nuloSeVazio(pedido.observacoes()));
        tarefa.setPrioridade(pedido.prioridade() != null ? pedido.prioridade() : Prioridade.MEDIA);
        tarefa.setMinutosEstimados(pedido.minutosEstimados());
        tarefa.setDataLimite(pedido.dataLimite());
        tarefa.setDataPlanejada(pedido.dataPlanejada());
        tarefa.setHoraPlanejada(pedido.horaPlanejada());
        tarefa.setHoraLembrete(pedido.horaLembrete());
    }

    private List<Tarefa> ordenar(List<Tarefa> tarefas) {
        return tarefas.stream().sorted(ORDEM_DE_EXECUCAO).toList();
    }

    private List<TarefaResponse> responder(List<Tarefa> tarefas, LocalDate hoje) {
        return tarefas.stream().map(tarefa -> TarefaResponse.de(tarefa, hoje)).toList();
    }

    private String nuloSeVazio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
