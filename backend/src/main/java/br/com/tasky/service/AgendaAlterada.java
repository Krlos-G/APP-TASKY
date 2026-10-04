package br.com.tasky.service;

/**
 * Algo que gera lembrete mudou: tarefa, habito, rotina, horario do resumo ou
 * fuso. Quem escuta gera de novo os lembretes daquele usuario.
 */
public record AgendaAlterada(Long usuarioId) {
}
