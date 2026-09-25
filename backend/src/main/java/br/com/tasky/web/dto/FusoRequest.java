package br.com.tasky.web.dto;

import jakarta.validation.constraints.NotBlank;

public record FusoRequest(
        @NotBlank(message = "informe o fuso horario") String fusoHorario) {
}
