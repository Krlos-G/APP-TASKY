package br.com.tasky.web.dto;

import br.com.tasky.entity.enums.StatusRegistroHabito;
import jakarta.validation.constraints.NotNull;

/** Desmarcar nao passa por aqui: e o DELETE da mesma rota. */
public record MarcacaoRequest(
        @NotNull(message = "informe o status") StatusRegistroHabito status) {
}
