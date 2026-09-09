package br.com.tasky.service;

import br.com.tasky.entity.BlocoModelo;
import br.com.tasky.entity.ModeloDia;
import br.com.tasky.entity.Usuario;
import br.com.tasky.entity.enums.DiaSemana;
import br.com.tasky.security.UsuarioAtual;
import br.com.tasky.web.dto.BlocoResponse;
import br.com.tasky.web.dto.DiaResponse;
import br.com.tasky.web.dto.HabitoDoDiaResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

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
    private final UsuarioAtual usuarioAtual;
    private final DataDoUsuario dataDoUsuario;

    public DiaAssembler(RotinaService rotinaService, HabitoService habitoService,
                        UsuarioAtual usuarioAtual, DataDoUsuario dataDoUsuario) {
        this.rotinaService = rotinaService;
        this.habitoService = habitoService;
        this.usuarioAtual = usuarioAtual;
        this.dataDoUsuario = dataDoUsuario;
    }

    /**
     * @param data o dia desejado, ou null para "hoje"
     */
    @Transactional(readOnly = true)
    public DiaResponse montar(LocalDate data) {
        Usuario usuario = usuarioAtual.obrigatorio();
        LocalDate dia = data != null ? data : dataDoUsuario.hoje(usuario);

        // O dia da semana sai da data ja resolvida no fuso do usuario. Calcular
        // isso no cliente daria resultado errado sempre que o relogio do
        // aparelho estivesse em outro fuso que o da conta.
        DiaSemana diaSemana = DiaSemana.de(dia.getDayOfWeek());

        // Habito independe de rotina: nao ter modelo atribuido ao sabado nao
        // significa nao ter habitos no sabado.
        List<HabitoDoDiaResponse> habitos = habitoService.doDia(usuario, dia);

        return rotinaService.modeloDoDia(usuario.getId(), diaSemana)
                .map(modelo -> DiaResponse.comRotina(dia, diaSemana.name(), modelo.getNome(),
                        blocosOrdenados(modelo), habitos))
                .orElseGet(() -> DiaResponse.semRotina(dia, diaSemana.name(), habitos));
    }

    private List<BlocoResponse> blocosOrdenados(ModeloDia modelo) {
        return modelo.getBlocos().stream()
                .sorted(Comparator.comparing(BlocoModelo::getHoraInicio)
                        .thenComparing(BlocoModelo::getHoraFim))
                .map(BlocoResponse::de)
                .toList();
    }
}
