package br.com.tasky.web.dto;

import br.com.tasky.entity.Habito;
import br.com.tasky.entity.enums.StatusRegistroHabito;

import java.time.LocalTime;

/**
 * @param status nulo quando o dia ainda nao foi marcado
 * @param streak o do habito hoje, nao o que ele tinha na data pedida
 */
public record HabitoDoDiaResponse(
        Long id,
        String nome,
        String icone,
        String cor,
        LocalTime horaPreferida,
        StatusRegistroHabito status,
        int streak) {

    public static HabitoDoDiaResponse de(Habito habito, StatusRegistroHabito status, int streak) {
        return new HabitoDoDiaResponse(
                habito.getId(),
                habito.getNome(),
                habito.getIcone(),
                habito.getCor(),
                habito.getHoraPreferida(),
                status,
                streak);
    }
}
