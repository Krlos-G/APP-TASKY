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
 * edicao de agenda, marcacao retroativa e virada de dia. Recalcular custa um
 * laco sobre poucos dias.
 *
 * A contagem anda para tras a partir de hoje e para no primeiro dia devido sem
 * marcacao. Dia que a agenda nao cobra e atravessado sem contar e sem quebrar -
 * e por isso que segunda-feira nao derruba o streak de um habito de terca.
 */
@Component
public class StreakCalculator {

    /**
     * Teto de seguranca do laco.
     *
     * Nenhuma contagem precisa olhar mais de um ano para tras, e o teto garante
     * que nenhuma combinacao estranha de datas vire um laco caro.
     */
    private static final int MAXIMO_DIAS = 366;

    /**
     * @param marcacoes o historico do habito indexado por data - so precisa
     *                  cobrir o periodo que a contagem vai percorrer
     * @param hoje      a data de hoje no fuso do usuario, nunca no do servidor
     * @param zona      usada para situar criacao e arquivamento, que sao Instant
     */
    public int calcular(Habito habito, Map<LocalDate, StatusRegistroHabito> marcacoes,
                        LocalDate hoje, ZoneId zona) {

        LocalDate dia = ultimoDiaContado(habito, hoje, zona);
        LocalDate nascimento = habito.getCriadoEm() == null
                ? null
                : LocalDate.ofInstant(habito.getCriadoEm(), zona);

        // O dia corrente ainda esta em aberto: enquanto ele nao acabar, nao ter
        // marcado ainda nao e ter falhado. Sem isto o numero zeraria toda
        // meia-noite e so voltaria depois da marcacao - o oposto do efeito que
        // o streak existe para ter.
        if (dia.equals(hoje) && habito.devidoEm(dia) && !marcacoes.containsKey(dia)) {
            dia = dia.minusDays(1);
        }

        int streak = 0;

        for (int passo = 0; passo < MAXIMO_DIAS; passo++) {
            // Antes de existir, o habito nao devia nada a ninguem.
            if (nascimento != null && dia.isBefore(nascimento)) {
                break;
            }

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

    /**
     * Onde a contagem comeca.
     *
     * Habito arquivado tem o streak congelado na data em que saiu de circulacao;
     * sem isso ele cairia a zero sozinho com o passar dos dias, apagando
     * justamente o historico que arquivar promete preservar.
     */
    private LocalDate ultimoDiaContado(Habito habito, LocalDate hoje, ZoneId zona) {
        if (!habito.isArquivado()) {
            return hoje;
        }
        LocalDate arquivamento = LocalDate.ofInstant(habito.getArquivadoEm(), zona);
        return arquivamento.isBefore(hoje) ? arquivamento : hoje;
    }
}
