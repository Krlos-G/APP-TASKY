package br.com.tasky.web.dto;

import java.time.Instant;

/**
 * O refresh token nao aparece aqui de proposito: ele viaja apenas no cookie
 * httpOnly, fora do alcance de qualquer script da pagina.
 */
public record TokenResponse(String accessToken, Instant expiraEm) {
}
