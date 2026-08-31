package br.com.tasky.seguranca;

/**
 * Identidade extraida do access token.
 *
 * Carrega o suficiente para atender a requisicao sem ir ao banco: o filtro de
 * autenticacao roda em toda chamada, e uma consulta por requisicao so para
 * redescobrir quem e o usuario seria desperdicio.
 */
public record UsuarioAutenticado(Long id, String email, String nome) {
}
