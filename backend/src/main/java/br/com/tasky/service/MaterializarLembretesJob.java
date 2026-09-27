package br.com.tasky.service;

import br.com.tasky.entity.Usuario;
import br.com.tasky.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MaterializarLembretesJob {

    private static final Logger log = LoggerFactory.getLogger(MaterializarLembretesJob.class);

    private final LembreteService lembreteService;
    private final UsuarioRepository usuarioRepository;

    public MaterializarLembretesJob(LembreteService lembreteService,
                                    UsuarioRepository usuarioRepository) {
        this.lembreteService = lembreteService;
        this.usuarioRepository = usuarioRepository;
    }

    /** Aos 5 minutos de cada hora, longe da virada onde tudo mais acontece. */
    @Scheduled(cron = "0 5 * * * *")
    public void materializar() {
        for (Usuario usuario : usuarioRepository.findAll()) {
            try {
                lembreteService.materializar(usuario);
            } catch (Exception e) {
                // Um usuario com dado estranho nao pode parar a fila dos outros.
                log.error("Falha ao materializar lembretes do usuario {}", usuario.getId(), e);
            }
        }
    }
}
