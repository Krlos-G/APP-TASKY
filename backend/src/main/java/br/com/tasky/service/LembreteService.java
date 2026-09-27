package br.com.tasky.service;

import br.com.tasky.entity.BlocoModelo;
import br.com.tasky.entity.Habito;
import br.com.tasky.entity.LembreteDia;
import br.com.tasky.entity.ModeloDia;
import br.com.tasky.entity.Tarefa;
import br.com.tasky.entity.Usuario;
import br.com.tasky.entity.enums.DiaSemana;
import br.com.tasky.entity.enums.StatusLembrete;
import br.com.tasky.entity.enums.StatusTarefa;
import br.com.tasky.entity.enums.TipoOrigemLembrete;
import br.com.tasky.repository.HabitoRepository;
import br.com.tasky.repository.LembreteDiaRepository;
import br.com.tasky.repository.TarefaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LembreteService {

    /** Hoje e amanha. O job roda de hora em hora e mantem a janela cheia. */
    private static final int DIAS_A_FRENTE = 2;

    private final LembreteDiaRepository lembreteRepository;
    private final HabitoRepository habitoRepository;
    private final TarefaRepository tarefaRepository;
    private final RotinaService rotinaService;
    private final DataDoUsuario dataDoUsuario;

    public LembreteService(LembreteDiaRepository lembreteRepository,
                           HabitoRepository habitoRepository,
                           TarefaRepository tarefaRepository,
                           RotinaService rotinaService,
                           DataDoUsuario dataDoUsuario) {
        this.lembreteRepository = lembreteRepository;
        this.habitoRepository = habitoRepository;
        this.tarefaRepository = tarefaRepository;
        this.rotinaService = rotinaService;
        this.dataDoUsuario = dataDoUsuario;
    }

    @Transactional
    public void materializar(Usuario usuario) {
        LocalDate hoje = dataDoUsuario.hoje(usuario);
        LocalDate fim = hoje.plusDays(DIAS_A_FRENTE - 1L);
        ZoneId zona = usuario.zona();

        List<Previsto> previstos = new ArrayList<>();
        for (LocalDate dia = hoje; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            previstos.addAll(dosBlocos(usuario, dia, zona));
            previstos.addAll(dosHabitos(usuario, dia, zona));
            doResumo(usuario, dia, zona).ifPresent(previstos::add);
        }
        previstos.addAll(dasTarefas(usuario, hoje, fim, zona));

        reconciliar(usuario, hoje, fim, previstos);
    }

    // ------------------------------------------------------------- previstos

    private List<Previsto> dosBlocos(Usuario usuario, LocalDate dia, ZoneId zona) {
        return rotinaService.modeloDoDia(usuario.getId(), DiaSemana.de(dia.getDayOfWeek()))
                .map(ModeloDia::getBlocos)
                .orElseGet(List::of)
                .stream()
                .filter(bloco -> bloco.getMinutosAntecedenciaLembrete() != null)
                .map(bloco -> new Previsto(
                        TipoOrigemLembrete.BLOCO,
                        bloco.getId(),
                        dia,
                        instante(dia, bloco.getHoraInicio(), zona)
                                .minusSeconds(bloco.getMinutosAntecedenciaLembrete() * 60L),
                        bloco.getTitulo(),
                        "Comeca as " + hhmm(bloco.getHoraInicio())))
                .toList();
    }

    private List<Previsto> dosHabitos(Usuario usuario, LocalDate dia, ZoneId zona) {
        return habitoRepository.findByUsuarioIdAndArquivadoEmIsNullOrderByNomeAsc(usuario.getId())
                .stream()
                .filter(habito -> habito.getHoraLembrete() != null && habito.devidoEm(dia))
                .map(habito -> new Previsto(
                        TipoOrigemLembrete.HABITO,
                        habito.getId(),
                        dia,
                        instante(dia, habito.getHoraLembrete(), zona),
                        habito.getNome(),
                        "Marque quando fizer."))
                .toList();
    }

    private List<Previsto> dasTarefas(Usuario usuario, LocalDate inicio, LocalDate fim,
                                      ZoneId zona) {
        return tarefaRepository.findByUsuarioIdAndStatusAndDataPlanejadaBetween(
                        usuario.getId(), StatusTarefa.A_FAZER, inicio, fim)
                .stream()
                .filter(tarefa -> tarefa.getHoraLembrete() != null)
                .map(tarefa -> new Previsto(
                        TipoOrigemLembrete.TAREFA,
                        tarefa.getId(),
                        tarefa.getDataPlanejada(),
                        instante(tarefa.getDataPlanejada(), tarefa.getHoraLembrete(), zona),
                        tarefa.getTitulo(),
                        "Tarefa de hoje."))
                .toList();
    }

    private Optional<Previsto> doResumo(Usuario usuario, LocalDate dia, ZoneId zona) {
        if (usuario.getHoraResumoDiario() == null) {
            return Optional.empty();
        }
        return Optional.of(new Previsto(
                TipoOrigemLembrete.RESUMO,
                null,
                dia,
                instante(dia, usuario.getHoraResumoDiario(), zona),
                "Bom dia",
                "Veja o que tem para hoje."));
    }

    // ---------------------------------------------------------- reconciliacao

    private void reconciliar(Usuario usuario, LocalDate inicio, LocalDate fim,
                             List<Previsto> previstos) {
        Map<Chave, LembreteDia> existentes = new HashMap<>();
        for (LembreteDia lembrete : lembreteRepository.findByUsuarioIdAndDataRefBetween(
                usuario.getId(), inicio, fim)) {
            existentes.put(Chave.de(lembrete), lembrete);
        }

        for (Previsto previsto : previstos) {
            LembreteDia existente = existentes.remove(previsto.chave());

            if (existente == null) {
                lembreteRepository.save(previsto.novo(usuario));
            } else if (existente.getStatus() == StatusLembrete.PENDENTE) {
                // Ja enviado nao se mexe: o passado nao muda por edicao.
                existente.setDispararEm(previsto.dispararEm());
                existente.setTitulo(previsto.titulo());
                existente.setCorpo(previsto.corpo());
            }
        }

        // O que sobrou perdeu a regra que o criou.
        existentes.values().stream()
                .filter(lembrete -> lembrete.getStatus() == StatusLembrete.PENDENTE)
                .forEach(lembrete -> lembrete.setStatus(StatusLembrete.CANCELADO));
    }

    // ------------------------------------------------------------------ apoio

    private static Instant instante(LocalDate dia, LocalTime hora, ZoneId zona) {
        return ZonedDateTime.of(dia, hora, zona).toInstant();
    }

    private static String hhmm(LocalTime hora) {
        return hora.toString().substring(0, 5);
    }

    private record Chave(TipoOrigemLembrete tipo, Long origemId, LocalDate data) {

        static Chave de(LembreteDia lembrete) {
            return new Chave(lembrete.getTipoOrigem(), lembrete.getOrigemId(),
                    lembrete.getDataRef());
        }
    }

    private record Previsto(TipoOrigemLembrete tipo, Long origemId, LocalDate data,
                            Instant dispararEm, String titulo, String corpo) {

        Chave chave() {
            return new Chave(tipo, origemId, data);
        }

        LembreteDia novo(Usuario usuario) {
            var lembrete = new LembreteDia();
            lembrete.setUsuario(usuario);
            lembrete.setTipoOrigem(tipo);
            lembrete.setOrigemId(origemId);
            lembrete.setDataRef(data);
            lembrete.setDispararEm(dispararEm);
            lembrete.setTitulo(titulo);
            lembrete.setCorpo(corpo);
            return lembrete;
        }
    }
}
