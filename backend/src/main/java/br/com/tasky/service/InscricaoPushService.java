package br.com.tasky.service;

import br.com.tasky.config.PushProperties;
import br.com.tasky.entity.InscricaoPush;
import br.com.tasky.entity.Usuario;
import br.com.tasky.notificacao.Notificacao;
import br.com.tasky.notificacao.Notificador;
import br.com.tasky.repository.InscricaoPushRepository;
import br.com.tasky.security.UsuarioAtual;
import br.com.tasky.web.dto.ChavePushResponse;
import br.com.tasky.web.dto.InscricaoPushRequest;
import br.com.tasky.web.dto.TesteEnvioResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class InscricaoPushService {

    private final InscricaoPushRepository repository;
    private final Notificador notificador;
    private final PushProperties propriedades;
    private final UsuarioAtual usuarioAtual;
    private final Clock clock;

    public InscricaoPushService(InscricaoPushRepository repository, Notificador notificador,
                                PushProperties propriedades, UsuarioAtual usuarioAtual,
                                Clock clock) {
        this.repository = repository;
        this.notificador = notificador;
        this.propriedades = propriedades;
        this.usuarioAtual = usuarioAtual;
        this.clock = clock;
    }

    public ChavePushResponse chave() {
        return new ChavePushResponse(propriedades.habilitado(), propriedades.chavePublica());
    }

    /**
     * O navegador renova a inscricao sozinho de tempos em tempos, e o endpoint
     * e unico no banco: reinscrever atualiza a linha em vez de duplicar.
     */
    @Transactional
    public void registrar(InscricaoPushRequest pedido) {
        Usuario usuario = usuarioAtual.obrigatorio();

        InscricaoPush inscricao = repository.findByEndpoint(pedido.endpoint())
                .orElseGet(InscricaoPush::new);

        inscricao.setUsuario(usuario);
        inscricao.setEndpoint(pedido.endpoint());
        inscricao.setP256dh(pedido.p256dh());
        inscricao.setAuth(pedido.auth());
        inscricao.setUserAgent(pedido.userAgent());
        inscricao.setVistoEm(clock.instant());

        repository.save(inscricao);
    }

    /** Silencioso quando nao existe: desativar duas vezes nao e erro. */
    @Transactional
    public void remover(String endpoint) {
        repository.findByEndpointAndUsuarioId(endpoint, usuarioAtual.idObrigatorio())
                .ifPresent(repository::delete);
    }

    @Transactional
    public TesteEnvioResponse testar() {
        Usuario usuario = usuarioAtual.obrigatorio();

        var resultado = notificador.enviarParaTodos(usuario, new Notificacao(
                "Tasky",
                "As notificacoes estao funcionando.",
                "/hoje",
                "TESTE"));

        return new TesteEnvioResponse(resultado.enviadas(), resultado.aparelhos());
    }
}
