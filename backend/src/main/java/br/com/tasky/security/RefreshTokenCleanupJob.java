package br.com.tasky.security;

import br.com.tasky.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * Remove refresh tokens vencidos ha bastante tempo.
 *
 * A carencia existe para nao apagar o rastro cedo demais: um token que acabou
 * de vencer ainda e util para entender um replay recente. Passado esse prazo,
 * a linha so ocupa espaco.
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);
    private static final Duration CARENCIA = Duration.ofDays(30);

    private final RefreshTokenRepository repository;
    private final Clock clock;

    public RefreshTokenCleanupJob(RefreshTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void limpar() {
        int apagados = repository.apagarExpiradosAntesDe(clock.instant().minus(CARENCIA));
        if (apagados > 0) {
            log.info("Limpeza removeu {} refresh tokens vencidos", apagados);
        }
    }
}
