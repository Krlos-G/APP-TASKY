package br.com.tasky.web.dto;

import br.com.tasky.entity.enums.Prioridade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/** @param prioridade nula vira MEDIA */
public record TarefaRequest(
        @NotBlank(message = "informe o titulo") @Size(max = 200) String titulo,
        @Size(max = 5000) String observacoes,
        Prioridade prioridade,
        @Positive(message = "a estimativa precisa ser maior que zero") Integer minutosEstimados,
        LocalDate dataLimite,
        LocalDate dataPlanejada,
        LocalTime horaPlanejada,
        LocalTime horaLembrete) {
}
