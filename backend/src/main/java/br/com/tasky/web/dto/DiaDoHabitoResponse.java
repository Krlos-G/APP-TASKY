package br.com.tasky.web.dto;

import br.com.tasky.entity.enums.StatusRegistroHabito;

import java.time.LocalDate;

/**
 * Um dia da janela recente de um habito.
 *
 * @param status nulo quando o dia nao foi marcado
 * @param devido false em dia de descanso planejado - nao ter marcado ali nao e
 *               falha, e a tela precisa dos dois separados para nao acusar
 *               buraco onde nao houve
 */
public record DiaDoHabitoResponse(LocalDate data, StatusRegistroHabito status, boolean devido) {
}
