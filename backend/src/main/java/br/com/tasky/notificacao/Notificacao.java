package br.com.tasky.notificacao;

/**
 * @param url  para onde levar quem tocar na notificacao
 * @param tag  agrupa: uma notificacao com a mesma tag substitui a anterior em
 *             vez de empilhar
 */
public record Notificacao(String titulo, String corpo, String url, String tag) {
}
