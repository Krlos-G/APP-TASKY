package br.com.tasky.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita @Scheduled na aplicacao.
 *
 * A instancia e unica, entao nao ha necessidade de lock distribuido. Se um dia
 * houver mais de uma replica, os jobs precisarao de coordenacao - o despachante
 * de lembretes da Fatia 7 e o caso critico.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
