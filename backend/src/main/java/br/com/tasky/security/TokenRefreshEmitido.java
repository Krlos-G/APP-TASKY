package br.com.tasky.security;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token recem-emitido.
 *
 * O valor em claro existe apenas aqui e no cookie enviado ao navegador: no
 * banco fica somente o hash. Quem vazar o banco nao consegue reconstruir
 * nenhum token.
 */
public record TokenRefreshEmitido(String valor, Instant expiraEm, UUID familia) {
}
