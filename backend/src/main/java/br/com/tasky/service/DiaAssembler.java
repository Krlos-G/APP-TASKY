package br.com.tasky.service;

import br.com.tasky.entity.BlocoModelo;
import br.com.tasky.entity.ModeloDia;
import br.com.tasky.entity.Usuario;
import br.com.tasky.entity.enums.DiaSemana;
import br.com.tasky.security.UsuarioAtual;
import br.com.tasky.web.dto.BlocoResponse;
import br.com.tasky.web.dto.DiaResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Monta a linha do tempo de um dia a partir da rotina do usuario.
 *
 * O modelo nunca e mutado pelo uso diario: o dia e derivado dele. Isso e o que
 * permite mudar a rotina sem reescrever o passado.
 */
@Service
public class DiaAssembler {

    private final RotinaService rotinaService;
    private final HabitoService habitoService;
    private final TarefaService tarefaService;
    private final UsuarioAtual usuarioAtual;
    private final DataDoUsuario dataDoUsuario;

    public DiaAssembler(RotinaService rotinaService, HabitoService habitoService,
                        TarefaService tarefaService, UsuarioAtual usuarioAtual,
                        DataDoUsuario dataDoUsuario) {
        this.rotinaService = rotinaService;
        this.habitoService = habitoService;
        this.tarefaService = tarefaService;
        this.usuarioAtual = usuarioAtual;
        this.dataDoUsuario = dataDoUsuario;
    }

    /**
     * @param data o dia desejado, ou null para "hoje"
     */
    @Transactional(readOnly = true)
    public DiaResponse montar(LocalDate data) {
        Usuario usuario = usuarioAtual.obrigatorio();
        LocalDate hoje = dataDoUsuario.hoje(usuario);
        LocalDate dia = data != null ? data : hoje;

        // O dia da semana sai da data ja resolvida no fuso do usuario. Calcular
        // isso no cliente daria resultado errado sempre que o relogio do
        // aparelho estivesse em outro fuso que o da conta.
        DiaSemana diaSemana = DiaSemana.de(dia.getDayOfWeek());
        Optional<ModeloDia> modelo = rotinaService.modeloDoDia(usuario.getId(), diaSemana);

        // Habitos e tarefas independem de rotina: sabado sem modelo atribuido
        // nao e sabado sem nada para fazer.
        return new DiaResponse(
                dia,
                diaSemana.name(),
                modelo.isPresent(),
                modelo.map(ModeloDia::getNome).orElse(null),
                modelo.map(this::blocosOrdenados).orElse(List.of()),
                habitoService.doDia(usuario, dia),
                tarefaService.doDia(usuario, dia),
                dia.equals(hoje) ? tarefaService.atrasadas(usuario) : List.of());
    }

    private List<BlocoResponse> blocosOrdenados(ModeloDia modelo) {
        return modelo.getBlocos().stream()
                .sorted(Comparator.comparing(BlocoModelo::getHoraInicio)
                        .thenComparing(BlocoModelo::getHoraFim))
                .map(BlocoResponse::de)
                .toList();
    }
}
