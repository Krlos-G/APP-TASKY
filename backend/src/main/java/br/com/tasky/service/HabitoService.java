package br.com.tasky.service;

import br.com.tasky.entity.Habito;
import br.com.tasky.entity.RegistroHabito;
import br.com.tasky.entity.Usuario;
import br.com.tasky.entity.enums.DiaSemana;
import br.com.tasky.entity.enums.StatusRegistroHabito;
import br.com.tasky.entity.enums.TipoAgenda;
import br.com.tasky.repository.HabitoRepository;
import br.com.tasky.repository.RegistroHabitoRepository;
import br.com.tasky.repository.projection.Marcacao;
import br.com.tasky.security.UsuarioAtual;
import br.com.tasky.web.ApiException;
import br.com.tasky.web.dto.HabitoRequest;
import br.com.tasky.web.dto.HabitoResponse;
import br.com.tasky.web.dto.MarcacaoResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Regras dos habitos: cadastro, agenda e arquivamento.
 *
 * Toda operacao parte do usuario autenticado e consulta filtrando por ele. Um
 * habito de outro dono responde como inexistente - dizer "existe, mas nao e
 * seu" ja entrega informacao a quem sonda.
 */
@Service
public class HabitoService {

    /** O teto do StreakCalculator mais o dia de tolerancia com que ele comeca. */
    private static final int DIAS_DE_HISTORICO = 367;

    private final HabitoRepository habitoRepository;
    private final RegistroHabitoRepository registroRepository;
    private final StreakCalculator streakCalculator;
    private final DataDoUsuario dataDoUsuario;
    private final UsuarioAtual usuarioAtual;
    private final Clock clock;

    public HabitoService(HabitoRepository habitoRepository,
                         RegistroHabitoRepository registroRepository,
                         StreakCalculator streakCalculator,
                         DataDoUsuario dataDoUsuario,
                         UsuarioAtual usuarioAtual,
                         Clock clock) {
        this.habitoRepository = habitoRepository;
        this.registroRepository = registroRepository;
        this.streakCalculator = streakCalculator;
        this.dataDoUsuario = dataDoUsuario;
        this.usuarioAtual = usuarioAtual;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<HabitoResponse> listar(boolean incluirArquivados) {
        Usuario usuario = usuarioAtual.obrigatorio();

        List<Habito> habitos = incluirArquivados
                ? habitoRepository.findByUsuarioIdOrderByNomeAsc(usuario.getId())
                : habitoRepository.findByUsuarioIdAndArquivadoEmIsNullOrderByNomeAsc(usuario.getId());

        return montar(habitos, usuario);
    }

    @Transactional
    public HabitoResponse criar(HabitoRequest pedido) {
        Usuario usuario = usuarioAtual.obrigatorio();

        var habito = new Habito();
        habito.setUsuario(usuario);
        aplicar(pedido, habito);

        return montarUm(habitoRepository.save(habito), usuario);
    }

    @Transactional
    public HabitoResponse atualizar(Long id, HabitoRequest pedido) {
        Usuario usuario = usuarioAtual.obrigatorio();
        Habito habito = buscar(id, usuario.getId());
        aplicar(pedido, habito);

        return montarUm(habitoRepository.save(habito), usuario);
    }

    @Transactional
    public HabitoResponse definirArquivo(Long id, boolean arquivado) {
        Usuario usuario = usuarioAtual.obrigatorio();
        Habito habito = buscar(id, usuario.getId());

        habito.setArquivadoEm(arquivado ? clock.instant() : null);

        return montarUm(habitoRepository.save(habito), usuario);
    }

    /** Apaga o habito e, por cascata no banco, todo o historico dele. */
    @Transactional
    public void apagar(Long id) {
        habitoRepository.delete(buscar(id, usuarioAtual.idObrigatorio()));
    }

    // -------------------------------------------------------------- marcacoes

    /**
     * Upsert: repetir a chamada troca o status da linha existente em vez de
     * esbarrar no unique (habito, data), e o cliente otimista pode reenviar.
     */
    @Transactional
    public HabitoResponse marcar(Long id, LocalDate data, StatusRegistroHabito status) {
        Usuario usuario = usuarioAtual.obrigatorio();
        Habito habito = buscar(id, usuario.getId());
        exigirDataPassadaOuHoje(data, usuario);

        RegistroHabito registro = registroRepository.findByHabitoIdAndData(id, data)
                .orElseGet(() -> {
                    var novo = new RegistroHabito();
                    novo.setHabito(habito);
                    novo.setData(data);
                    return novo;
                });
        registro.setStatus(status);
        registroRepository.save(registro);

        return montarUm(habito, usuario);
    }

    @Transactional
    public HabitoResponse desmarcar(Long id, LocalDate data) {
        Usuario usuario = usuarioAtual.obrigatorio();
        Habito habito = buscar(id, usuario.getId());
        exigirDataPassadaOuHoje(data, usuario);

        registroRepository.apagar(id, data);

        return montarUm(habito, usuario);
    }

    @Transactional(readOnly = true)
    public List<MarcacaoResponse> historico(Long id, LocalDate desde, LocalDate ate) {
        Usuario usuario = usuarioAtual.obrigatorio();
        buscar(id, usuario.getId());

        LocalDate fim = ate != null ? ate : dataDoUsuario.hoje(usuario);
        LocalDate inicio = desde != null ? desde : fim.minusDays(29);

        return registroRepository.marcacoesDoHabito(id, usuario.getId(), inicio, fim).stream()
                .map(MarcacaoResponse::de)
                .toList();
    }

    // ------------------------------------------------------------------ apoio

    private Habito buscar(Long id, Long usuarioId) {
        return habitoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> ApiException.naoEncontrado("Habito"));
    }

    private void aplicar(HabitoRequest pedido, Habito habito) {
        // A agenda sem dia fixo cobra por semana, nao por dia: aceita-la aqui
        // criaria um habito que nunca aparece no Hoje e tem streak sempre zero.
        if (pedido.tipoAgenda() == TipoAgenda.VEZES_POR_SEMANA) {
            throw ApiException.agendaNaoSuportada();
        }

        habito.setNome(pedido.nome().trim());
        habito.setIcone(nuloSeVazio(pedido.icone()));
        habito.setCor(nuloSeVazio(pedido.cor()));
        habito.setTipoAgenda(pedido.tipoAgenda());
        habito.setDiasSemana(diasDaAgenda(pedido));
        habito.setHoraPreferida(pedido.horaPreferida());
        habito.setHoraLembrete(pedido.horaLembrete());
        habito.setMetaSemanal(null);
    }

    private Set<DiaSemana> diasDaAgenda(HabitoRequest pedido) {
        if (pedido.tipoAgenda() != TipoAgenda.DIAS_SEMANA) {
            return EnumSet.noneOf(DiaSemana.class);
        }
        if (pedido.diasSemana() == null || pedido.diasSemana().isEmpty()) {
            throw ApiException.diasDaSemanaVazios();
        }
        return EnumSet.copyOf(pedido.diasSemana());
    }

    /**
     * Futuro medido no fuso da conta: as 23h em Sao Paulo ja e o dia seguinte
     * num servidor em UTC, e o usuario levaria 400 marcando o proprio hoje.
     */
    private void exigirDataPassadaOuHoje(LocalDate data, Usuario usuario) {
        if (data.isAfter(dataDoUsuario.hoje(usuario))) {
            throw ApiException.dataNoFuturo();
        }
    }

    private String nuloSeVazio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private HabitoResponse montarUm(Habito habito, Usuario usuario) {
        return montar(List.of(habito), usuario).getFirst();
    }

    private List<HabitoResponse> montar(List<Habito> habitos, Usuario usuario) {
        if (habitos.isEmpty()) {
            return List.of();
        }

        LocalDate hoje = dataDoUsuario.hoje(usuario);
        ZoneId zona = usuario.zona();

        Map<Long, Map<LocalDate, StatusRegistroHabito>> historico = registroRepository
                .marcacoesDoUsuario(usuario.getId(), inicioDaJanela(habitos, hoje, zona), hoje)
                .stream()
                .collect(Collectors.groupingBy(Marcacao::habitoId,
                        Collectors.toMap(Marcacao::data, Marcacao::status)));

        return habitos.stream()
                .map(habito -> {
                    Map<LocalDate, StatusRegistroHabito> marcacoes =
                            historico.getOrDefault(habito.getId(), Map.of());
                    return HabitoResponse.de(
                            habito,
                            streakCalculator.calcular(habito, marcacoes, hoje, zona),
                            marcacoes.get(hoje),
                            habito.devidoEm(hoje));
                })
                .toList();
    }

    private LocalDate inicioDaJanela(List<Habito> habitos, LocalDate hoje, ZoneId zona) {
        LocalDate maisAntigo = habitos.stream()
                .filter(Habito::isArquivado)
                .map(habito -> LocalDate.ofInstant(habito.getArquivadoEm(), zona))
                .filter(data -> data.isBefore(hoje))
                .min(Comparator.naturalOrder())
                .orElse(hoje);

        return maisAntigo.minusDays(DIAS_DE_HISTORICO);
    }
}
