package br.com.tasky.service;

import br.com.tasky.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gera os lembretes do usuario logo depois que ele salva algo que tem lembrete.
 *
 * Sem isto os lembretes so nasciam na rodada de hora em hora, e um lembrete
 * marcado para antes da proxima rodada chegava atrasado ou se perdia.
 */
@Component
public class LembretesAoSalvar {

    private static final Logger log = LoggerFactory.getLogger(LembretesAoSalvar.class);

    private final LembreteService lembreteService;
    private final UsuarioRepository usuarioRepository;
    private final TransactionTemplate novaTransacao;

    public LembretesAoSalvar(LembreteService lembreteService,
                             UsuarioRepository usuarioRepository,
                             PlatformTransactionManager transacoes) {
        this.lembreteService = lembreteService;
        this.usuarioRepository = usuarioRepository;
        // Depois do commit a transacao de quem salvou ainda esta presa a thread,
        // ja encerrada: entrar nela faria a geracao rodar e nao gravar nada.
        this.novaTransacao = new TransactionTemplate(transacoes);
        this.novaTransacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Depois do commit: so gera a partir do que foi gravado de fato. */
    @TransactionalEventListener(fallbackExecution = true)
    public void gerar(AgendaAlterada evento) {
        try {
            novaTransacao.executeWithoutResult(status ->
                    usuarioRepository.findById(evento.usuarioId()).ifPresent(lembreteService::materializar));
        } catch (RuntimeException e) {
            // O que o usuario salvou ja esta gravado: falhar aqui viraria um erro
            // na tela para algo que deu certo. A rodada de hora em hora corrige.
            log.error("Falha ao gerar os lembretes do usuario {} depois de salvar", evento.usuarioId(), e);
        }
    }
}
