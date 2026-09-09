package br.com.tasky.web.dto;

import br.com.tasky.entity.enums.DiaSemana;
import br.com.tasky.entity.enums.TipoAgenda;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.Set;

/**
 * @param diasSemana   obrigatorio quando tipoAgenda e DIAS_SEMANA, ignorado nos
 *                     demais casos. A coerencia entre os dois e checada no
 *                     servico, que sabe traduzir a falta em mensagem util
 * @param horaPreferida so ordena a lista do dia; nao agenda nada
 * @param horaLembrete  nulo = sem lembrete para este habito (Fatia 7)
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
