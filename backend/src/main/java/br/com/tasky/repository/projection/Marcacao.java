package br.com.tasky.repository.projection;

import br.com.tasky.entity.enums.StatusRegistroHabito;

import java.time.LocalDate;

public record Marcacao(Long habitoId, LocalDate data, StatusRegistroHabito status) {
}
