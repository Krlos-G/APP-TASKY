package br.com.tasky.service;

import br.com.tasky.RelogioAjustavel;
import br.com.tasky.TestcontainersConfiguration;
import br.com.tasky.entity.Habito;
import br.com.tasky.entity.InscricaoPush;
import br.com.tasky.entity.LembreteDia;
import br.com.tasky.entity.RegistroHabito;
import br.com.tasky.entity.Tarefa;
import br.com.tasky.entity.Usuario;
import br.com.tasky.entity.enums.StatusLembrete;
import br.com.tasky.entity.enums.StatusRegistroHabito;
import br.com.tasky.entity.enums.StatusTarefa;
import br.com.tasky.entity.enums.TipoAgenda;
import br.com.tasky.entity.enums.TipoOrigemLembrete;
import br.com.tasky.notificacao.CanalDeTeste;
import br.com.tasky.notificacao.ResultadoEnvio;
import br.com.tasky.repository.HabitoRepository;
import br.com.tasky.repository.InscricaoPushRepository;
import br.com.tasky.repository.LembreteDiaRepository;
import br.com.tasky.repository.RegistroHabitoRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O despacho dos lembretes.
 *
 * O que interessa aqui e o que NAO sai: item ja resolvido, lembrete atrasado e
 * conta sem aparelho. Uma notificacao a mais e pior que nenhuma.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, DespachoLembreteServiceTest.RelogioDeTesteConfig.class})
class DespachoLembreteServiceTest {

    /** Segunda-feira, 28 de setembro de 2026, 19:00 em Sao Paulo. */
    private static final Instant AGORA = Instant.parse("2026-09-28T22:00:00Z");
    private static final LocalDate HOJE = LocalDate.parse("2026-09-28");

    @TestConfiguration
    static class RelogioDeTesteConfig {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return new RelogioAjustavel(AGORA);
        }

        @Bean
        @Primary
        CanalDeTeste canalDeTeste() {
            return new CanalDeTeste();
        }
    }

    @Autowired private DespachoLembreteService despacho;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private HabitoRepository habitoRepository;
    @Autowired private RegistroHabitoRepository registroRepository;
    @Autowired private TarefaRepository tarefaRepository;
    @Autowired private LembreteDiaRepository lembreteRepository;
    @Autowired private InscricaoPushRepository inscricaoRepository;
    @Autowired private CanalDeTeste canal;
    @Autowired private Clock clock;

    private Usuario usuario;

    @BeforeEach
    void preparar() {
        ((RelogioAjustavel) clock).definir(AGORA);
        canal.limpar();

        lembreteRepository.deleteAll();
        inscricaoRepository.deleteAll();
        registroRepository.deleteAll();
        tarefaRepository.deleteAll();
        habitoRepository.deleteAll();
        usuarioRepository.deleteAll();

        var novo = new Usuario();
        novo.setEmail("carlos@tasky.app");
        novo.setSenhaHash("nao-importa");
        novo.setNomeExibicao("Carlos");
        novo.setFusoHorario("America/Sao_Paulo");
        usuario = usuarioRepository.save(novo);

        inscricao("https://web.push.apple.com/iphone");
    }

    // ------------------------------------------------------------- entrega

    @Test
    @DisplayName("o lembrete no horario sai e vira enviado")
    void enviaNoHorario() {
        Habito ler = habito("Ler");
        LembreteDia lembrete = lembrete(TipoOrigemLembrete.HABITO, ler.getId(), AGORA);

        despachar();

        assertThat(canal.enviadas).singleElement()
                .satisfies(n -> {
                    assertThat(n.titulo()).isEqualTo("Ler");
                    assertThat(n.url()).isEqualTo("/hoje");
                    assertThat(n.tag()).isEqualTo("HABITO-" + ler.getId());
                });
        assertThat(recarregar(lembrete))
                .satisfies(l -> {
                    assertThat(l.getStatus()).isEqualTo(StatusLembrete.ENVIADO);
                    assertThat(l.getEnviadoEm()).isEqualTo(AGORA);
                });
    }

    @Test
    @DisplayName("despachar de novo nao reenvia o que ja saiu")
    void naoReenvia() {
        habitoComLembrete();

        despachar();
        despachar();

        assertThat(canal.enviadas).hasSize(1);
    }

    @Test
    @DisplayName("lembrete do futuro nem entra na fila")
    void futuroNaoEntra() {
        Habito ler = habito("Ler");
        lembrete(TipoOrigemLembrete.HABITO, ler.getId(), AGORA.plusSeconds(600));

        assertThat(despacho.vencidos()).isEmpty();
    }

    // ------------------------------------------------------------ o que nao sai

    @Test
    @DisplayName("lembrete atrasado mais de 30 min e descartado")
    void atrasadoDemais() {
        Habito ler = habito("Ler");
        LembreteDia lembrete = lembrete(
                TipoOrigemLembrete.HABITO, ler.getId(), AGORA.minusSeconds(31 * 60));

        despachar();

        assertThat(canal.enviadas).isEmpty();
        assertThat(recarregar(lembrete).getStatus()).isEqualTo(StatusLembrete.CANCELADO);
    }

    @Test
    @DisplayName("atraso de meia hora ainda passa")
    void atrasoNoLimite() {
        Habito ler = habito("Ler");
        lembrete(TipoOrigemLembrete.HABITO, ler.getId(), AGORA.minusSeconds(30 * 60));

        despachar();

        assertThat(canal.enviadas).hasSize(1);
    }

    @Test
    @DisplayName("habito ja marcado nao gera empurrao")
    void habitoJaMarcado() {
        Habito ler = habito("Ler");
        marcar(ler, HOJE);
        LembreteDia lembrete = lembrete(TipoOrigemLembrete.HABITO, ler.getId(), AGORA);

        despachar();

        assertThat(canal.enviadas).isEmpty();
        assertThat(recarregar(lembrete).getStatus()).isEqualTo(StatusLembrete.CANCELADO);
    }

    @Test
    @DisplayName("habito arquivado no meio do caminho nao gera empurrao")
    void habitoArquivado() {
        Habito ler = habito("Ler");
        ler.setArquivadoEm(AGORA);
        habitoRepository.save(ler);
        lembrete(TipoOrigemLembrete.HABITO, ler.getId(), AGORA);

        despachar();

        assertThat(canal.enviadas).isEmpty();
    }

    @Test
    @DisplayName("tarefa ja concluida nao gera empurrao")
    void tarefaConcluida() {
        Tarefa tarefa = tarefa("Responder e-mail", StatusTarefa.FEITA);
        LembreteDia lembrete = lembrete(TipoOrigemLembrete.TAREFA, tarefa.getId(), AGORA);

        despachar();

        assertThat(canal.enviadas).isEmpty();
        assertThat(recarregar(lembrete).getStatus()).isEqualTo(StatusLembrete.CANCELADO);
    }

    @Test
    @DisplayName("item apagado depois de materializado nao gera empurrao")
    void origemApagada() {
        Tarefa tarefa = tarefa("Responder e-mail", StatusTarefa.A_FAZER);
        lembrete(TipoOrigemLembrete.TAREFA, tarefa.getId(), AGORA);
        tarefaRepository.delete(tarefa);

        despachar();

        assertThat(canal.enviadas).isEmpty();
    }

    @Test
    @DisplayName("sem aparelho inscrito, o lembrete e cancelado em vez de ficar tentando")
    void semAparelho() {
        inscricaoRepository.deleteAll();
        LembreteDia lembrete = habitoComLembrete();

        despachar();

        assertThat(recarregar(lembrete).getStatus()).isEqualTo(StatusLembrete.CANCELADO);
    }

    // --------------------------------------------------------------- retentativa

    @Test
    @DisplayName("falha de rede mantem pendente e desiste na terceira")
    void falhaTemporaria() {
        LembreteDia lembrete = habitoComLembrete();
        canal.resposta = inscricao -> ResultadoEnvio.FALHA_TEMPORARIA;

        despachar();
        assertThat(recarregar(lembrete))
                .satisfies(l -> {
                    assertThat(l.getStatus()).isEqualTo(StatusLembrete.PENDENTE);
                    assertThat(l.getTentativas()).isEqualTo(1);
                });

        despachar();
        despachar();

        assertThat(recarregar(lembrete))
                .satisfies(l -> {
                    assertThat(l.getStatus()).isEqualTo(StatusLembrete.FALHOU);
                    assertThat(l.getTentativas()).isEqualTo(3);
                });
    }

    @Test
    @DisplayName("aparelho que sumiu e apagado, e o lembrete nao fica tentando")
    void inscricaoExpirada() {
        LembreteDia lembrete = habitoComLembrete();
        canal.resposta = inscricao -> ResultadoEnvio.INSCRICAO_EXPIRADA;

        despachar();

        assertThat(inscricaoRepository.count()).isZero();
        assertThat(recarregar(lembrete).getStatus()).isEqualTo(StatusLembrete.CANCELADO);
    }

    @Test
    @DisplayName("o resumo diario nao depende de nenhum item para sair")
    void resumoSempreSai() {
        var lembrete = new LembreteDia();
        lembrete.setUsuario(usuario);
        lembrete.setTipoOrigem(TipoOrigemLembrete.RESUMO);
        lembrete.setDataRef(HOJE);
        lembrete.setDispararEm(AGORA);
        lembrete.setTitulo("Bom dia");
        lembrete.setCorpo("Veja o que tem para hoje.");
        lembreteRepository.save(lembrete);

        despachar();

        assertThat(canal.enviadas).singleElement()
                .satisfies(n -> assertThat(n.tag()).isEqualTo("RESUMO-" + HOJE));
    }

    // ------------------------------------------------------------------ apoio

    private void despachar() {
        despacho.vencidos().forEach(despacho::despachar);
    }

    private LembreteDia recarregar(LembreteDia lembrete) {
        return lembreteRepository.findById(lembrete.getId()).orElseThrow();
    }

    private LembreteDia habitoComLembrete() {
        return lembrete(TipoOrigemLembrete.HABITO, habito("Ler").getId(), AGORA);
    }

    private LembreteDia lembrete(TipoOrigemLembrete tipo, Long origemId, Instant dispararEm) {
        var lembrete = new LembreteDia();
        lembrete.setUsuario(usuario);
        lembrete.setTipoOrigem(tipo);
        lembrete.setOrigemId(origemId);
        lembrete.setDataRef(HOJE);
        lembrete.setDispararEm(dispararEm);
        lembrete.setTitulo(tipo == TipoOrigemLembrete.HABITO ? "Ler" : "Responder e-mail");
        lembrete.setCorpo("Marque quando fizer.");
        return lembreteRepository.save(lembrete);
    }

    private Habito habito(String nome) {
        var habito = new Habito();
        habito.setUsuario(usuario);
        habito.setNome(nome);
        habito.setTipoAgenda(TipoAgenda.DIARIO);
        habito.setHoraLembrete(LocalTime.parse("19:00"));
        return habitoRepository.save(habito);
    }

    private void marcar(Habito habito, LocalDate data) {
        var registro = new RegistroHabito();
        registro.setHabito(habito);
        registro.setData(data);
        registro.setStatus(StatusRegistroHabito.FEITO);
        registroRepository.save(registro);
    }

    private Tarefa tarefa(String titulo, StatusTarefa status) {
        var tarefa = new Tarefa();
        tarefa.setUsuario(usuario);
        tarefa.setTitulo(titulo);
        tarefa.setDataPlanejada(HOJE);
        tarefa.setHoraLembrete(LocalTime.parse("19:00"));
        tarefa.setStatus(status);
        return tarefaRepository.save(tarefa);
    }

    private void inscricao(String endpoint) {
        var inscricao = new InscricaoPush();
        inscricao.setUsuario(usuario);
        inscricao.setEndpoint(endpoint);
        inscricao.setP256dh("chave");
        inscricao.setAuth("segredo");
        inscricaoRepository.save(inscricao);
    }
}
