package br.com.tasky.web.dto;

import br.com.tasky.entity.Habito;
import br.com.tasky.entity.enums.DiaSemana;
import br.com.tasky.entity.enums.StatusRegistroHabito;
import br.com.tasky.entity.enums.TipoAgenda;

import java.time.LocalTime;
import java.util.Set;

/**
 * Um habito com o que a tela precisa saber sobre ele hoje.
 *
 * O streak nao vem do banco: e recalculado a cada leitura pelo StreakCalculator.
 *
 * @param statusHoje nulo quando o dia ainda nao foi marcado - o que e diferente
 *                   de ter sido pulado, e a tela precisa distinguir os dois
 * @param devidoHoje false num habito de terca visto numa segunda; a tela usa
 *                   isso para nao cobrar o que a agenda nao pede
 */
public record HabitoResponse(
        Long id,
        String nome,
        String icone,
        String cor,
        TipoAgenda tipoAgenda,
        Set<DiaSemana> diasSemana,
        LocalTime horaPreferida,
        LocalTime horaLembrete,
        boolean arquivado,
        int streak,
        StatusRegistroHabito statusHoje,
        boolean devidoHoje) {

    public static HabitoResponse de(Habito habito, int streak,
                                    StatusRegistroHabito statusHoje, boolean devidoHoje) {
        return new HabitoResponse(
                habito.getId(),
                habito.getNome(),
                habito.getIcone(),
                habito.getCor(),
                habito.getTipoAgenda(),
                Set.copyOf(habito.getDiasSemana()),
                habito.getHoraPreferida(),
                habito.getHoraLembrete(),
                habito.isArquivado(),
                streak,
                statusHoje,
                devidoHoje);
    }
}
