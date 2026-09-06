package br.com.tasky.web.dto;

import br.com.tasky.entity.enums.DiaSemana;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Atribuicao dos sete dias, enviada de uma vez.
 *
 * E substituicao do conjunto, nao incremento: o cliente manda a semana inteira
 * e o servidor reconcilia. Assim nao existe estado meio-atualizado se uma
 * chamada falhar no meio. Dia ausente ou com valor nulo significa "sem rotina".
 */
public record SemanaRequest(
        @NotNull(message = "informe a atribuicao dos dias")
        Map<DiaSemana, Long> modeloPorDia) {
}
