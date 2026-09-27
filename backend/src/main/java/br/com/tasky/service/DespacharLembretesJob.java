package br.com.tasky.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Roda a cada minuto, entao um lembrete pode sair ate ~60s depois da hora.
 * Aceitavel para um empurrao, e muito mais simples que agendar cada um.
 */
@Component
public class DespacharLembretesJob {

    private static final Logger log = LoggerFactory.getLogger(DespacharLembretesJob.class);

    private final DespachoLembreteService despacho;

    public DespacharLembretesJob(DespachoLembreteService despacho) {
        this.despacho = despacho;
    }

    @Scheduled(fixedDelay = 60_000)
    public void despachar() {
        for (Long id : despacho.vencidos()) {
            try {
                despacho.despachar(id);
            } catch (Exception e) {
                log.error("Falha ao despachar o lembrete {}", id, e);
            }
        }
    }
}
