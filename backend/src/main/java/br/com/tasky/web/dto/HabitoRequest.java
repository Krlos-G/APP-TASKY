package br.com.tasky.web.dto;

import br.com.tasky.entity.enums.DiaSemana;
import br.com.tasky.entity.enums.TipoAgenda;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.Set;

/**
 * @param diasSemana obrigatorio quando tipoAgenda e DIAS_SEMANA, ignorado nos
 *                   demais casos
 */
public record HabitoRequest(
        @NotBlank(message = "informe o nome") @Size(max = 100) String nome,
        @Size(max = 40) String icone,
        @Size(max = 20) String cor,
        @NotNull(message = "informe o tipo de agenda") TipoAgenda tipoAgenda,
        Set<DiaSemana> diasSemana,
        LocalTime horaPreferida,
        LocalTime horaLembrete) {
}
