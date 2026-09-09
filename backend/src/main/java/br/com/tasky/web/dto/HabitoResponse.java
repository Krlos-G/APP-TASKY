package br.com.tasky.web.dto;

import br.com.tasky.entity.Habito;
import br.com.tasky.entity.enums.DiaSemana;
import br.com.tasky.entity.enums.StatusRegistroHabito;
import br.com.tasky.entity.enums.TipoAgenda;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * @param statusHoje nulo quando o dia ainda nao foi marcado - diferente de ter
 *                   sido pulado, e a tela precisa distinguir os dois
 */
public record HabitoResponse(
        Long id,
        String nome,
        String icone,
        String cor,
        TipoAgenda tipoAgenda,
        List<DiaSemana> diasSemana,
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
                habito.getDiasSemana().stream().sorted(Comparator.naturalOrder()).toList(),
                habito.getHoraPreferida(),
                habito.getHoraLembrete(),
                habito.isArquivado(),
                streak,
                statusHoje,
                devidoHoje);
    }
}
