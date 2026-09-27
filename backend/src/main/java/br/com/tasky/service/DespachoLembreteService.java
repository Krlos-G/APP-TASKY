package br.com.tasky.service;

import br.com.tasky.entity.LembreteDia;
import br.com.tasky.entity.Tarefa;
import br.com.tasky.entity.enums.StatusLembrete;
import br.com.tasky.entity.enums.StatusTarefa;
import br.com.tasky.notificacao.Notificacao;
import br.com.tasky.notificacao.Notificador;
import br.com.tasky.repository.BlocoModeloRepository;
import br.com.tasky.repository.HabitoRepository;
import br.com.tasky.repository.LembreteDiaRepository;
import br.com.tasky.repository.RegistroHabitoRepository;
import br.com.tasky.repository.TarefaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Entrega os lembretes vencidos.
 *
 * Antes de enviar, revalida: um lembrete so sai se aquilo que ele lembra ainda
 * existe e ainda esta pendente. A materializacao roda de hora em hora, entao
 * entre uma passada e outra o mundo muda.
 */
@Service
public class DespachoLembreteService {

    /** Passou disso, o empurrao virou ruido - e melhor calar do que chegar tarde. */
    private static final Duration ATRASO_MAXIMO = Duration.ofMinutes(30);

    private static final int MAXIMO_TENTATIVAS = 3;

    private static final Logger log = LoggerFactory.getLogger(DespachoLembreteService.class);

    private final LembreteDiaRepository lembreteRepository;
    private final HabitoRepository habitoRepository;
    private final RegistroHabitoRepository registroRepository;
    private final TarefaRepository tarefaRepository;
    private final BlocoModeloRepository blocoRepository;
    private final Notificador notificador;
    private final Clock clock;

    public DespachoLembreteService(LembreteDiaRepository lembreteRepository,
                                   HabitoRepository habitoRepository,
                                   RegistroHabitoRepository registroRepository,
                                   TarefaRepository tarefaRepository,
                                   BlocoModeloRepository blocoRepository,
                                   Notificador notificador,
                                   Clock clock) {
        this.lembreteRepository = lembreteRepository;
        this.habitoRepository = habitoRepository;
        this.registroRepository = registroRepository;
        this.tarefaRepository = tarefaRepository;
        this.blocoRepository = blocoRepository;
        this.notificador = notificador;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Long> vencidos() {
        return lembreteRepository
                .findByStatusAndDispararEmLessThanEqualOrderByDispararEm(
                        StatusLembrete.PENDENTE, clock.instant())
                .stream()
                .map(LembreteDia::getId)
                .toList();
    }

    /** Uma transacao por lembrete: um problema num nao derruba a fila. */
    @Transactional
    public void despachar(Long id) {
        LembreteDia lembrete = lembreteRepository.findById(id).orElse(null);
        if (lembrete == null || lembrete.getStatus() != StatusLembrete.PENDENTE) {
            return;
        }

        Instant agora = clock.instant();

        if (Duration.between(lembrete.getDispararEm(), agora).compareTo(ATRASO_MAXIMO) > 0) {
            cancelar(lembrete, "atrasado demais");
            return;
        }

        if (!aindaFazSentido(lembrete)) {
            cancelar(lembrete, "o item ja foi resolvido ou nao existe mais");
            return;
        }

        var resultado = notificador.enviarParaTodos(lembrete.getUsuario(), notificacaoDe(lembrete));

        if (resultado.enviadas() > 0) {
            lembrete.setStatus(StatusLembrete.ENVIADO);
            lembrete.setEnviadoEm(agora);
            return;
        }

        // Sem falha de rede, nao ha o que tentar de novo: ou nao havia aparelho,
        // ou os que havia sumiram e ja foram apagados.
        if (resultado.falhas() == 0) {
            cancelar(lembrete, "nenhum aparelho para receber");
            return;
        }

        lembrete.setTentativas(lembrete.getTentativas() + 1);
        if (lembrete.getTentativas() >= MAXIMO_TENTATIVAS) {
            lembrete.setStatus(StatusLembrete.FALHOU);
            log.warn("Lembrete {} desistiu depois de {} tentativas", id, MAXIMO_TENTATIVAS);
        }
    }

    // ------------------------------------------------------------------ apoio

    /**
     * O que o lembrete lembra ainda existe e ainda esta por fazer?
     *
     * O bloco nao tem conclusao no MVP, entao para ele basta continuar existindo.
     */
    private boolean aindaFazSentido(LembreteDia lembrete) {
        Long dono = lembrete.getUsuario().getId();

        return switch (lembrete.getTipoOrigem()) {
            case HABITO -> habitoRepository.findByIdAndUsuarioId(lembrete.getOrigemId(), dono)
                    .filter(habito -> !habito.isArquivado())
                    .filter(habito -> registroRepository
                            .findByHabitoIdAndData(habito.getId(), lembrete.getDataRef())
                            .isEmpty())
                    .isPresent();

            case TAREFA -> tarefaRepository.findByIdAndUsuarioId(lembrete.getOrigemId(), dono)
                    .map(Tarefa::getStatus)
                    .filter(status -> status == StatusTarefa.A_FAZER)
                    .isPresent();

            case BLOCO -> blocoRepository
                    .findByIdAndModeloDiaUsuarioId(lembrete.getOrigemId(), dono)
                    .isPresent();

            case RESUMO -> true;
        };
    }

    private Notificacao notificacaoDe(LembreteDia lembrete) {
        String tag = lembrete.getOrigemId() != null
                ? lembrete.getTipoOrigem() + "-" + lembrete.getOrigemId()
                : lembrete.getTipoOrigem() + "-" + lembrete.getDataRef();

        // Tudo leva para o Hoje: e de la que qualquer um dos itens se resolve.
        return new Notificacao(lembrete.getTitulo(), lembrete.getCorpo(), "/hoje", tag);
    }

    private void cancelar(LembreteDia lembrete, String motivo) {
        lembrete.setStatus(StatusLembrete.CANCELADO);
        log.debug("Lembrete {} cancelado: {}", lembrete.getId(), motivo);
    }
}
