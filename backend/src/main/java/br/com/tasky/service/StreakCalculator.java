package br.com.tasky.service;

import br.com.tasky.entity.Habito;
import br.com.tasky.entity.enums.StatusRegistroHabito;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * Quantos dias seguidos o habito foi cumprido.
 *
 * Nao e armazenado em coluna: seria estado derivado, e desatualizaria a cada
 * edicao de agenda, marcacao retroativa e virada de dia.
 */
@Component
public class StreakCalculator {

    private static final int MAXIMO_DIAS = 366;

    public int calcular(Habito habito, Map<LocalDate, StatusRegistroHabito> marcacoes,
                        LocalDate hoje, ZoneId zona) {

        LocalDate dia = ultimoDiaContado(habito, hoje, zona);

        // O dia de hoje ainda esta em aberto: nao ter marcado ainda nao e ter
        // falhado. Sem isto o numero zeraria toda meia-noite e so voltaria
        // depois da marcacao - o oposto do efeito que o streak existe para ter.
        if (dia.equals(hoje) && habito.devidoEm(dia) && !marcacoes.containsKey(dia)) {
            dia = dia.minusDays(1);
        }

        int streak = 0;

        for (int passo = 0; passo < MAXIMO_DIAS; passo++) {
            if (habito.devidoEm(dia)) {
                StatusRegistroHabito status = marcacoes.get(dia);
                if (status == null) {
                    break;
                }
                if (status == StatusRegistroHabito.FEITO) {
                    streak++;
                }
                // PULADO e descanso planejado: nao soma, mas tambem nao quebra.
            }

            dia = dia.minusDays(1);
        }

        return streak;
    }

    /** Habito arquivado congela o streak na data em que saiu de circulacao. */
    private LocalDate ultimoDiaContado(Habito habito, LocalDate hoje, ZoneId zona) {
        if (!habito.isArquivado()) {
            return hoje;
        }
        LocalDate arquivamento = LocalDate.ofInstant(habito.getArquivadoEm(), zona);
        return arquivamento.isBefore(hoje) ? arquivamento : hoje;
    }
}
