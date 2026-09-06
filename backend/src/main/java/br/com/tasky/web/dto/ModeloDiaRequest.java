package br.com.tasky.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModeloDiaRequest(
        @NotBlank(message = "informe o nome") @Size(max = 100) String nome,
        boolean padrao) {
}
