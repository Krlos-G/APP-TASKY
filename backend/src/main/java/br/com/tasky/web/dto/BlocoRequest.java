package br.com.tasky.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record BlocoRequest(
        @NotBlank(message = "informe o titulo") @Size(max = 200) String titulo,
        @NotNull(message = "informe a hora de inicio") LocalTime horaInicio,
        @NotNull(message = "informe a hora de fim") LocalTime horaFim,
        @Size(max = 20) String cor,
        @PositiveOrZero(message = "a antecedencia nao pode ser negativa")
        Integer minutosAntecedenciaLembrete) {
}
