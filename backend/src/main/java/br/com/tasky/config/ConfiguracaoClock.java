package br.com.tasky.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Fonte unica de "agora" na aplicacao.
 *
 * Nada de Instant.now() solto: tudo que precisa da hora atual recebe este
 * Clock injetado, o que torna testavel a virada de dia, o disparo de
 * lembretes e o calculo de streak (basta injetar Clock.fixed nos testes).
 */
@Configuration
public class ConfiguracaoClock {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
